package io.github.kafeg.inkpeek.stardict

import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

/** One headword hit: the word as written in the dictionary and its article. */
data class Hit(val word: String, val html: String)

fun normKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFC).replace("­", "").trim().lowercase(Locale.ROOT)

private val EDGE = Regex("^[\\p{P}\\p{S}\\s]+|[\\p{P}\\p{S}\\s]+$")
fun cleanQuery(s: String): String = s.replace("­", "").replace(Regex("\\s+"), " ").replace(EDGE, "")

/**
 * A StarDict 3 dictionary: <base>.ifo, .idx[.gz], .dict[.dz], optional .syn.
 * Its own sorted index lives in [workRoot]/<id>/, never in the dictionary folder.
 */
class StarDict(ifoFile: File, workRoot: File) {
    val ifo: File = ifoFile.canonicalFile
    val props: Map<String, String> = ifo.readLines(Charsets.UTF_8).mapNotNull { l ->
        val i = l.indexOf('='); if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim() else null
    }.toMap()
    val name: String = props["bookname"] ?: ifo.nameWithoutExtension
    val id: String = sha1(ifo.path.lowercase()).take(16) // shared storage is case-insensitive
    private val base = ifo.path.removeSuffix(".ifo")
    val workDir = File(workRoot, id)
    private val indexFile = File(workDir, "index.ipk")
    private val types = props["sametypesequence"]
    private val offset64 = props["idxoffsetbits"] == "64"
    @Volatile private var map: MappedByteBuffer? = null
    @Volatile private var data: DictData? = null

    val ready: Boolean get() = indexFile.exists() && indexFile.lastModified() >= ifo.lastModified()

    /** Builds the index if missing or older than the .ifo. Slow for big dictionaries: call off the main thread. */
    @Synchronized fun prepare() {
        if (ready) return
        workDir.deleteRecursively(); workDir.mkdirs()
        File(workDir, "source").writeText(ifo.path)
        buildIndex()
        map = null
    }

    private fun readMaybeGz(name: String): ByteArray? = File(name).takeIf { it.exists() }?.readBytes()
        ?: File("$name.gz").takeIf { it.exists() }?.let { f -> GZIPInputStream(f.inputStream()).use { it.readBytes() } }
        ?: File("$name.dz").takeIf { it.exists() }?.let { f -> GZIPInputStream(f.inputStream()).use { it.readBytes() } }

    /** Low memory: one "key\u0000<word number>" string per entry, sorted in place, then streamed to disk. */
    private fun buildIndex() {
        val idx = readMaybeGz("$base.idx") ?: error("no .idx")
        var n = 0; var p = 0
        while (p < idx.size) { while (idx[p] != 0.toByte()) p++; p += if (offset64) 13 else 9; n++ }
        val wordAt = IntArray(n); val offs = LongArray(n); val sizes = IntArray(n)
        val b = ByteBuffer.wrap(idx); p = 0
        for (i in 0 until n) {
            wordAt[i] = p; while (idx[p] != 0.toByte()) p++
            b.position(p + 1); offs[i] = if (offset64) b.long else b.int.toLong() and 0xFFFFFFFFL; sizes[i] = b.int; p = b.position()
        }
        fun word(i: Int): String { var e = wordAt[i]; while (idx[e] != 0.toByte()) e++; return String(idx, wordAt[i], e - wordAt[i], Charsets.UTF_8) }
        fun tag(key: String, w: Int) = key + '\u0000' + Integer.toHexString(w)
        val syn = readMaybeGz("$base.syn")
        var synN = 0
        if (syn != null) { p = 0; while (p < syn.size) { while (syn[p] != 0.toByte()) p++; p += 5; synN++ } }
        val keys = arrayOfNulls<String>(n + synN); var k = 0
        for (i in 0 until n) keys[k++] = tag(normKey(word(i)), i)
        if (syn != null) {
            val s = ByteBuffer.wrap(syn); p = 0
            repeat(synN) {
                val start = p; while (syn[p] != 0.toByte()) p++
                val form = String(syn, start, p - start, Charsets.UTF_8); s.position(p + 1); val w = s.int; p = s.position()
                if (w in 0 until n) keys[k++] = tag(normKey(form), w)
            }
        }
        val all = keys.copyOf(k).requireNoNulls(); java.util.Arrays.sort(all)
        val blobFile = File(workDir, "blob.tmp"); val pos = IntArray(all.size); var count = 0
        DataOutputStream(blobFile.outputStream().buffered(1 shl 16)).use { o ->
            var prev: String? = null
            for (t in all) {
                if (t == prev) continue; prev = t
                val cut = t.lastIndexOf('\u0000'); val w = Integer.parseInt(t.substring(cut + 1), 16)
                val kb = t.substring(0, cut).toByteArray(); val wb = word(w).toByteArray()
                pos[count++] = o.size()
                o.writeShort(kb.size); o.write(kb); o.writeShort(wb.size); o.write(wb); o.writeLong(offs[w]); o.writeInt(sizes[w])
            }
        }
        val tmp = File(workDir, "index.tmp")
        DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { o ->
            o.writeInt(MAGIC); o.writeInt(count); for (i in 0 until count) o.writeInt(pos[i])
            blobFile.inputStream().use { it.copyTo(o) }
        }
        blobFile.delete(); tmp.renameTo(indexFile)
    }

    private fun mapped(): MappedByteBuffer = map ?: RandomAccessFile(indexFile, "r").use {
        it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
    }.also { map = it }

    private fun dict(): DictData = data ?: DictData.open(File("$base.dict"), File("$base.dict.dz")).also { data = it }

    fun lookup(query: String): List<Hit> {
        prepare()
        val key = normKey(query); if (key.isEmpty()) return emptyList()
        val m = mapped(); val n = m.getInt(4); val blob = 8 + 4 * n
        fun keyAt(i: Int): String { val p = blob + m.getInt(8 + 4 * i); return str(m, p + 2, m.getShort(p).toInt() and 0xFFFF) }
        var lo = 0; var hi = n - 1; var first = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1; val c = keyAt(mid).compareTo(key)
            if (c < 0) lo = mid + 1 else { if (c == 0) first = mid; hi = mid - 1 }
        }
        if (first < 0) return emptyList()
        // All entries for the key (cheap: headwords only), then the best 8 get their articles read.
        // Order: the word as written (same case) > a form of another word (.syn) > the word in another case
        // ("lag" is a form of "liegen" before the noun "Lag"; "Häuser" leads to "Haus").
        val q = query.trim()
        class E(val word: String, val off: Long, val size: Int)
        val all = ArrayList<E>(); var i = first
        while (i < n && keyAt(i) == key) {
            var p = blob + m.getInt(8 + 4 * i)
            val kl = m.getShort(p).toInt() and 0xFFFF; p += 2 + kl
            val wl = m.getShort(p).toInt() and 0xFFFF; val word = str(m, p + 2, wl); p += 2 + wl
            all += E(word, m.getLong(p), m.getInt(p + 8)); i++
        }
        val best = all.distinctBy { it.off }.sortedBy { e -> when { e.word == q -> 0; !e.word.equals(q, true) -> 1; else -> 2 } }.take(8)
        val d = dict()
        return best.map { Hit(it.word, Article.toHtml(d.read(it.off, it.size), types)) }.distinctBy { it.html }
    }

    private fun str(m: MappedByteBuffer, p: Int, len: Int): String {
        val a = ByteArray(len); for (j in 0 until len) a[j] = m.get(p + j); return String(a, Charsets.UTF_8)
    }

    companion object {
        private const val MAGIC = 0x49504B32 // "IPK2"
        fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        /** Every .ifo below [root] (any depth). */
        fun scan(root: File): List<File> =
            if (!root.isDirectory) emptyList() else root.walkTopDown().maxDepth(6).filter { it.isFile && it.name.endsWith(".ifo") }.toList()
    }
}

/** Article bytes from a plain .dict, or from a .dict.dz by dictzip chunks (falls back to inflating it whole). */
abstract class DictData {
    abstract fun read(off: Long, size: Int): ByteArray

    private class Plain(f: File) : DictData() {
        private val raf = RandomAccessFile(f, "r")
        @Synchronized override fun read(off: Long, size: Int) = ByteArray(size).also { raf.seek(off); raf.readFully(it) }
    }

    private class Whole(val bytes: ByteArray) : DictData() {
        override fun read(off: Long, size: Int) = bytes.copyOfRange(off.toInt(), off.toInt() + size)
    }

    /** dictzip: gzip with an "RA" extra field listing compressed chunk sizes; each chunk inflates on its own. */
    private class Dictzip(f: File, val chunkLen: Int, val starts: LongArray) : DictData() {
        private val raf = RandomAccessFile(f, "r")
        @Synchronized override fun read(off: Long, size: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(size)
            var c = (off / chunkLen).toInt(); val end = off + size
            while (c < starts.size - 1 && c.toLong() * chunkLen < end) {
                val comp = ByteArray((starts[c + 1] - starts[c]).toInt()); raf.seek(starts[c]); raf.readFully(comp)
                val inf = java.util.zip.Inflater(true); inf.setInput(comp)
                val buf = ByteArray(chunkLen); val got = inf.inflate(buf); inf.end()
                out.write(buf, 0, got); c++
            }
            val all = out.toByteArray(); val from = (off - (off / chunkLen) * chunkLen).toInt()
            return all.copyOfRange(from, from + size)
        }
    }

    companion object {
        fun open(plain: File, dz: File): DictData {
            if (plain.exists()) return Plain(plain)
            return runCatching { dictzip(dz) }.getOrNull()
                ?: Whole(GZIPInputStream(dz.inputStream().buffered()).use { it.readBytes() })
        }

        private fun dictzip(f: File): DictData? {
            RandomAccessFile(f, "r").use { r ->
                val h = ByteArray(10); r.readFully(h)
                if (h[0] != 0x1f.toByte() || h[1] != 0x8b.toByte() || (h[3].toInt() and 4) == 0) return null
                val flags = h[3].toInt()
                val xlen = r.readUnsignedByte() or (r.readUnsignedByte() shl 8)
                val extra = ByteArray(xlen); r.readFully(extra)
                val e = ByteBuffer.wrap(extra).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                var chunkLen = 0; var sizes: IntArray? = null
                while (e.remaining() >= 4) {
                    val si1 = e.get().toInt().toChar(); val si2 = e.get().toInt().toChar(); val len = e.short.toInt() and 0xFFFF
                    val sub = e.position()
                    if (si1 == 'R' && si2 == 'A') {
                        e.short; chunkLen = e.short.toInt() and 0xFFFF; val cnt = e.short.toInt() and 0xFFFF
                        sizes = IntArray(cnt) { e.short.toInt() and 0xFFFF }
                    }
                    e.position(sub + len)
                }
                if (sizes == null || chunkLen == 0) return null
                if (flags and 8 != 0) while (r.readByte() != 0.toByte()) {}
                if (flags and 16 != 0) while (r.readByte() != 0.toByte()) {}
                if (flags and 2 != 0) r.skipBytes(2)
                val starts = LongArray(sizes.size + 1); starts[0] = r.filePointer
                for (i in sizes.indices) starts[i + 1] = starts[i] + sizes[i]
                return Dictzip(f, chunkLen, starts)
            }
        }
    }
}

/** StarDict article data -> simple HTML for a TextView. */
object Article {
    fun toHtml(data: ByteArray, types: String?): String {
        val out = StringBuilder(); var p = 0
        fun field(t: Char, last: Boolean): ByteArray {
            if (t.isUpperCase()) { // binary: 32-bit size first
                val s = ByteBuffer.wrap(data, p, 4).int; p += 4; return data.copyOfRange(p, p + s).also { p += s }
            }
            if (last) return data.copyOfRange(p, data.size).also { p = data.size }
            var e = p; while (e < data.size && data[e] != 0.toByte()) e++
            return data.copyOfRange(p, e).also { p = e + 1 }
        }
        fun add(t: Char, bytes: ByteArray) {
            val s = String(bytes, Charsets.UTF_8)
            when (t) {
                'h', 'g' -> out.append(s)
                'x' -> out.append(xdxf(s))
                'm', 'l', 't', 'y' -> out.append(escape(s).replace("\n", "<br>"))
                else -> {}
            }
            out.append("<br>")
        }
        if (types != null) types.forEachIndexed { i, t -> if (p < data.size) add(t, field(t, i == types.length - 1)) }
        else while (p < data.size) { val t = data[p++].toInt().toChar(); add(t, field(t, false)) }
        return tidy(out.toString().removeSuffix("<br>"))
    }

    /** Calmer HTML for a small card: headings become bold lines, links plain text, images and styles go. */
    fun tidy(h: String) = h.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
        .replace(Regex("(?i)<h[1-6][^>]*>"), "<br><b>").replace(Regex("(?i)</h[1-6]>"), "</b><br>")
        .replace(Regex("(?i)</?a\\b[^>]*>"), "").replace(Regex("(?i)<img[^>]*>"), "")
        .replace(Regex("(?i)(<br\\s*/?>\\s*){3,}"), "<br><br>").removePrefix("<br>")

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun xdxf(s: String) = s.replace("<k>", "<b>").replace("</k>", "</b><br>")
        .replace("<ex>", "<i>").replace("</ex>", "</i>").replace("<abr>", "<i>").replace("</abr>", "</i>")
        .replace("<kref>", "<u>").replace("</kref>", "</u>").replace(Regex("</?(c|co|dtrn|ar|def|gr|tr)[^>]*>"), "")
        .replace("\n", "<br>")
}
