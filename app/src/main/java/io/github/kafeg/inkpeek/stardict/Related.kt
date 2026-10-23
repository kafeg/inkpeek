package io.github.kafeg.inkpeek.stardict

import java.io.File
import java.io.RandomAccessFile

/**
 * A pack's word families, synonyms, expressions, forms, pronunciation and origin (packs/: <id>.rel): one line per word,
 * sorted, tab-separated. Found by binary search over the file's bytes: the file is never loaded.
 */
class Related(private val file: File) {
    /** [rel], [syn], [ant], [expr]: frequent first; [forms]: "form" to its tags; [rank]: in the frequency list (0: not
     *  in it); [sep]: a separable verb's particle ("zumachen" → "zu"), else "". */
    data class Entry(val rel: List<String>, val syn: List<String>, val ant: List<String>, val expr: List<String>,
                     val ipa: List<String>, val origin: String, val forms: List<Pair<String, String>>, val rank: Int = 0, val sep: String = "")

    @Synchronized fun of(word: String): Entry? = runCatching { RandomAccessFile(file, "r").use { find(it, word) } }.getOrNull()?.let(::parse)

    private fun find(f: RandomAccessFile, word: String): String? {
        val key = word.toByteArray(Charsets.UTF_8)
        var lo = 0L; var hi = f.length()
        val len = f.length(); val block = ByteArray(16384)
        // The first line starting at or after [at]: (its start, its bytes); read in blocks, not byte by byte.
        fun lineAt(at: Long): Pair<Long, ByteArray>? {
            var start = -1L; var p = if (at > 0) at - 1 else 0
            val out = java.io.ByteArrayOutputStream()
            if (at == 0L) start = 0
            while (p < len) {
                f.seek(p); val n = f.read(block); if (n <= 0) break
                for (i in 0 until n) {
                    if (start < 0) { if (block[i] == '\n'.code.toByte()) start = p + i + 1; continue }
                    if (block[i] == '\n'.code.toByte()) return start to out.toByteArray()
                    out.write(block[i].toInt())
                }
                p += n
            }
            return if (start in 0 until len) start to out.toByteArray() else null
        }
        // The line starting at or after mid: before the key → the key is after it; after → the key starts before mid.
        while (lo < hi) {
            val mid = (lo + hi) / 2
            val l = lineAt(mid)
            if (l == null) { hi = mid; continue }
            val c = compare(l.second, key)
            if (c == 0) return String(l.second, Charsets.UTF_8)
            if (c < 0) lo = l.first + l.second.size + 1 else hi = mid
        }
        return lineAt(lo)?.second?.takeIf { compare(it, key) == 0 }?.let { String(it, Charsets.UTF_8) }
    }

    /** The line's word (up to its tab) against [key], byte by byte: the order Python sorted the words in (code points). */
    private fun compare(line: ByteArray, key: ByteArray): Int {
        val end = line.indexOf('\t'.code.toByte()).let { if (it < 0) line.size else it }
        for (i in 0 until minOf(end, key.size)) {
            val a = line[i].toInt() and 0xFF; val b = key[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return end - key.size
    }

    private fun parse(line: String): Entry {
        val c = line.split('\t')
        fun list(i: Int) = c.getOrNull(i)?.split('|')?.filter { it.isNotBlank() }.orEmpty()
        return Entry(list(1), list(2), list(3), list(4), list(5), c.getOrNull(6).orEmpty(),
            list(7).map { it.substringBeforeLast(':') to it.substringAfterLast(':', "") }, c.getOrNull(8)?.toIntOrNull() ?: 0, c.getOrNull(9).orEmpty())
    }
}
