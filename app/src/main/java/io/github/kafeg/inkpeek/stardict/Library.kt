package io.github.kafeg.inkpeek.stardict

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Dictionaries from [roots] (own folder first, then extra folders such as /sdcard/dictdata).
 * Order and the disabled set are kept by the caller as dictionary ids.
 */
class Library(private val roots: List<File>, private val workRoot: File) {
    private var cache: Map<String, StarDict> = emptyMap()

    @Synchronized fun all(order: List<String> = emptyList()): List<StarDict> {
        val found = roots.flatMap { StarDict.scan(it) }.distinctBy { it.absolutePath }
        val byPath = cache.values.associateBy { it.ifo.absolutePath }
        cache = found.map { byPath[it.absolutePath] ?: StarDict(it, workRoot) }.associateBy { it.id }
        val rank = order.withIndex().associate { it.value to it.index }
        return cache.values.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.name }))
    }

    /** Hits per dictionary, in [order], skipping [disabled]; only dictionaries with a hit. */
    fun lookup(query: String, order: List<String>, disabled: Set<String>): List<Pair<StarDict, List<Hit>>> {
        val q = cleanQuery(query)
        return all(order).filter { it.id !in disabled }.mapNotNull { d ->
            runCatching { d.lookup(q) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { d to it }
        }
    }
}

/** Unpacks an archive or a set of loose StarDict files into [target]/<dictionary>/. */
object Importer {
    private val DICT_EXT = listOf(".ifo", ".idx", ".idx.gz", ".dict", ".dict.dz", ".syn", ".syn.dz")

    /** Returns the imported dictionary folders. */
    fun importStream(name: String, input: InputStream, target: File, tmpRoot: File): List<File> {
        val tmp = File(tmpRoot, "import-" + System.nanoTime()).apply { mkdirs() }
        try {
            val n = name.lowercase()
            val arch: ArchiveInputStream<*>? = when {
                n.endsWith(".zip") -> ZipArchiveInputStream(input.buffered())
                n.endsWith(".tar") -> TarArchiveInputStream(input.buffered())
                n.endsWith(".tar.gz") || n.endsWith(".tgz") -> TarArchiveInputStream(GZIPInputStream(input.buffered()))
                n.endsWith(".tar.bz2") -> TarArchiveInputStream(BZip2CompressorInputStream(input.buffered()))
                n.endsWith(".tar.xz") -> TarArchiveInputStream(XZCompressorInputStream(input.buffered()))
                n.endsWith(".tar.zst") -> TarArchiveInputStream(ZstdCompressorInputStream(input.buffered()))
                DICT_EXT.any { n.endsWith(it) } -> null
                else -> throw IllegalArgumentException("Unsupported file: $name")
            }
            if (arch == null) File(tmp, File(name).name).outputStream().use { input.copyTo(it) }
            else arch.use { a ->
                while (true) {
                    val e = a.nextEntry ?: break
                    if (e.isDirectory) continue
                    val out = File(tmp, e.name).canonicalFile
                    if (!out.path.startsWith(tmp.canonicalPath + File.separator)) continue
                    out.parentFile?.mkdirs(); out.outputStream().use { a.copyTo(it) }
                }
            }
            return collect(tmp, target)
        } finally { tmp.deleteRecursively() }
    }

    /** Moves every <base>.ifo with its sibling files from [from] into its own folder under [target]. */
    fun collect(from: File, target: File): List<File> = StarDict.scan(from).map { ifo ->
        val base = ifo.name.removeSuffix(".ifo")
        val name = ifo.readLines().firstOrNull { it.startsWith("bookname=") }?.substringAfter('=')?.trim() ?: base
        var dir = File(target, safe(name)); var i = 2
        while (dir.exists()) dir = File(target, safe(name) + "-" + i++)
        dir.mkdirs()
        ifo.parentFile!!.listFiles()!!.filter { f -> DICT_EXT.any { f.name == base + it } }.forEach { f ->
            val dst = File(dir, f.name); if (!f.renameTo(dst)) { f.copyTo(dst, true); f.delete() }
        }
        dir
    }

    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "dict" }
}
