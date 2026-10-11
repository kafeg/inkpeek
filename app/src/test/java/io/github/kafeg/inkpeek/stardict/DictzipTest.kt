package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.Deflater

class DictzipTest {
    /** Writes [data] as dictzip: gzip + "RA" extra field, every chunk flushed so it inflates on its own. */
    private fun dictzip(data: ByteArray, f: File, chunk: Int = 1000) {
        val chunks = ArrayList<ByteArray>()
        val d = Deflater(6, true)
        var p = 0
        while (p < data.size) {
            val end = minOf(p + chunk, data.size); d.setInput(data, p, end - p)
            val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
            while (true) { val n = d.deflate(buf, 0, buf.size, Deflater.FULL_FLUSH); out.write(buf, 0, n); if (n < buf.size) break }
            chunks += out.toByteArray(); p = end
        }
        d.finish(); val tail = ByteArrayOutputStream(); val buf = ByteArray(64)
        while (!d.finished()) { val n = d.deflate(buf); tail.write(buf, 0, n) }
        val extra = ByteBuffer.allocate(4 + 6 + 2 * chunks.size).order(ByteOrder.LITTLE_ENDIAN)
        extra.put('R'.code.toByte()).put('A'.code.toByte()).putShort((6 + 2 * chunks.size).toShort())
        extra.putShort(1).putShort(chunk.toShort()).putShort(chunks.size.toShort()); chunks.forEach { extra.putShort(it.size.toShort()) }
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0x1f, 0x8b.toByte(), 8, 4, 0, 0, 0, 0, 2, 3))
        o.write(extra.capacity() and 0xff); o.write(extra.capacity() shr 8); o.write(extra.array())
        chunks.forEach { o.write(it) }; o.write(tail.toByteArray())
        val crc = CRC32().apply { update(data) }.value
        o.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(crc.toInt()).putInt(data.size).array())
        f.writeBytes(o.toByteArray())
    }

    @Test fun readsAcrossChunks() {
        val data = (0 until 25_000).joinToString(" ") { "w$it" }.toByteArray()
        val dir = Files.createTempDirectory("dz").toFile(); val f = File(dir, "t.dict.dz"); dictzip(data, f)
        val dd = DictData.open(File(dir, "t.dict"), f)
        for ((off, size) in listOf(0 to 10, 995 to 30, 1999 to 2, 5000 to 3500, data.size - 7 to 7))
            assertArrayEquals(data.copyOfRange(off, off + size), dd.read(off.toLong(), size))
    }
}
