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
 * A StarDict 3 dictionary folder: <base>.ifo, .idx[.gz], .dict[.dz], optional .syn.
 * Own sorted index and the plain .dict live in [workDir], never in the dictionary folder.
 */
class StarDict(val ifo: File, workRoot: File) {
    val props: Map<String, String> = ifo.readLines(Charsets.UTF_8).mapNotNull { l ->
        val i = l.indexOf('='); if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim() else null
    }.toMap()
    val name: String = props["bookname"] ?: ifo.nameWithoutExtension
    val id: String = sha1(ifo.absolutePath).take(16)
    private val base = ifo.path.removeSuffix(".ifo")
    private val workDir = File(workRoot, id)
    private val indexFile = File(workDir, "index.ipk")
    private val dictFile = File(workDir, "dict")
    private val types = props["sametypesequence"]
    private val offset64 = props["idxoffsetbits"] == "64"
    @Volatile private var map: MappedByteBuffer? = null

    val ready: Boolean get() = indexFile.exists() && dictFile.exists() && indexFile.lastModified() >= ifo.lastModified()

    /** Builds the index and the plain .dict if missing or older than the .ifo. */
    @Synchronized fun prepare() {
        if (ready) return
        workDir.mkdirs()
        val dz = File("$base.dict.dz"); val plain = File("$base.dict")
        val tmpDict = File(workDir, "dict.tmp")
        if (plain.exists()) plain.copyTo(tmpDict, true)
        else GZIPInputStream(dz.inputStream().buffered()).use { i -> tmpDict.outputStream().use { i.copyTo(it) } }
        tmpDict.renameTo(dictFile)
        buildIndex()
        map = null
    }

    private fun buildIndex() {
        val idx = File("$base.idx").takeIf { it.exists() }?.readBytes()
            ?: GZIPInputStream(File("$base.idx.gz").inputStream()).use { it.readBytes() }
        val words = ArrayList<String>(); val offs = ArrayList<Long>(); val sizes = ArrayList<Int>()
        val b = ByteBuffer.wrap(idx)
        while (b.hasRemaining()) {
            val start = b.position(); while (idx[b.position()] != 0.toByte()) b.position(b.position() + 1)
            words += String(idx, start, b.position() - start, Charsets.UTF_8); b.get()
            offs += if (offset64) b.long else b.int.toLong() and 0xFFFFFFFFL
            sizes += b.int
        }
        class E(val key: String, val w: Int)
        val entries = ArrayList<E>(words.size * 2)
        for (i in words.indices) entries += E(normKey(words[i]), i)
        File("$base.syn").takeIf { it.exists() }?.readBytes()?.let { syn ->
            val s = ByteBuffer.wrap(syn)
            while (s.hasRemaining()) {
                val start = s.position(); while (syn[s.position()] != 0.toByte()) s.position(s.position() + 1)
                val form = String(syn, start, s.position() - start, Charsets.UTF_8); s.get()
                val w = s.int
                if (w in words.indices) entries += E(normKey(form), w)
            }
        }
        val sorted = entries.distinctBy { it.key to it.w }.sortedBy { it.key }
        val tmp = File(workDir, "index.tmp")
        val blob = java.io.ByteArrayOutputStream(); val pos = IntArray(sorted.size)
        DataOutputStream(blob).use { o ->
            sorted.forEachIndexed { i, e ->
                pos[i] = o.size()
                val k = e.key.toByteArray(); val w = words[e.w].toByteArray()
                o.writeShort(k.size); o.write(k); o.writeShort(w.size); o.write(w); o.writeLong(offs[e.w]); o.writeInt(sizes[e.w])
            }
        }
        DataOutputStream(tmp.outputStream().buffered()).use { o ->
            o.writeInt(MAGIC); o.writeInt(sorted.size); pos.forEach { o.writeInt(it) }; blob.writeTo(o)
        }
        tmp.renameTo(indexFile)
    }

    private fun mapped(): MappedByteBuffer = map ?: RandomAccessFile(indexFile, "r").use {
        it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
    }.also { map = it }

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
        val hits = ArrayList<Hit>()
        RandomAccessFile(dictFile, "r").use { d ->
            var i = first
            while (i < n && keyAt(i) == key && hits.size < 8) {
                var p = blob + m.getInt(8 + 4 * i)
                val kl = m.getShort(p).toInt() and 0xFFFF; p += 2 + kl
                val wl = m.getShort(p).toInt() and 0xFFFF; val word = str(m, p + 2, wl); p += 2 + wl
                val off = m.getLong(p); val size = m.getInt(p + 8)
                val data = ByteArray(size); d.seek(off); d.readFully(data)
                hits += Hit(word, Article.toHtml(data, types))
                i++
            }
        }
        return hits.distinctBy { it.html }
    }

    private fun str(m: MappedByteBuffer, p: Int, len: Int): String {
        val a = ByteArray(len); for (j in 0 until len) a[j] = m.get(p + j); return String(a, Charsets.UTF_8)
    }

    companion object {
        private const val MAGIC = 0x49504B31 // "IPK1"
        fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        /** Every .ifo below [root] (any depth). */
        fun scan(root: File): List<File> =
            if (!root.isDirectory) emptyList() else root.walkTopDown().maxDepth(6).filter { it.isFile && it.name.endsWith(".ifo") }.toList()
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
        return out.toString().removeSuffix("<br>")
    }

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun xdxf(s: String) = s.replace("<k>", "<b>").replace("</k>", "</b><br>")
        .replace("<ex>", "<i>").replace("</ex>", "</i>").replace("<abr>", "<i>").replace("</abr>", "</i>")
        .replace("<kref>", "<u>").replace("</kref>", "</u>").replace(Regex("</?(c|co|dtrn|ar|def|gr|tr)[^>]*>"), "")
        .replace("\n", "<br>")
}
