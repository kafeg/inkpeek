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

/** What an import brought: the new dictionaries' folders, and the names of those that were there already. */
data class Imported(val added: List<File>, val already: List<String>)

/** Unpacks an archive or loose StarDict files into [target]/<dictionary>/; a dictionary already there is not added twice. */
object Importer {
    private val DICT_EXT = listOf(".ifo", ".idx", ".idx.gz", ".dict", ".dict.dz", ".syn", ".syn.dz")

    fun isDictFile(name: String) = DICT_EXT.any { name.lowercase().endsWith(it) }

    fun importStream(name: String, input: InputStream, target: File, tmpRoot: File): Imported {
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
                isDictFile(n) -> null
                else -> throw IllegalArgumentException("not a StarDict file or archive: $name")
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
            return collect(tmp, target).also { if (it.added.isEmpty() && it.already.isEmpty()) throw IllegalArgumentException("no StarDict dictionary (.ifo) in $name") }
        } finally { tmp.deleteRecursively() }
    }

    /**
     * Moves every <base>.ifo with its sibling files from [from] into its own folder under [target]; one whose .ifo and
     * index are already in [target] (downloaded or imported again) is left out.
     */
    fun collect(from: File, target: File): Imported {
        val have = StarDict.scan(target).map { it.readText() to idxSize(it) }.toSet()
        val added = ArrayList<File>(); val already = ArrayList<String>()
        StarDict.scan(from).forEach { ifo ->
            val base = ifo.name.removeSuffix(".ifo")
            val name = ifo.readLines().firstOrNull { it.startsWith("bookname=") }?.substringAfter('=')?.trim() ?: base
            if ((ifo.readText() to idxSize(ifo)) in have) { already += name; return@forEach }
            added += place(ifo, base, name, target)
        }
        return Imported(added, already)
    }

    private fun idxSize(ifo: File) = listOf(".idx", ".idx.gz").map { File(ifo.parentFile, ifo.name.removeSuffix(".ifo") + it) }
        .firstOrNull { it.exists() }?.length() ?: -1L

    private fun place(ifo: File, base: String, name: String, target: File): File {
        var dir = File(target, safe(name)); var i = 2
        while (dir.exists()) dir = File(target, safe(name) + "-" + i++)
        dir.mkdirs()
        ifo.parentFile!!.listFiles()!!.filter { f -> DICT_EXT.any { f.name == base + it } }.forEach { f ->
            val dst = File(dir, f.name); if (!f.renameTo(dst)) { f.copyTo(dst, true); f.delete() }
        }
        return dir
    }

    /**
     * Deletes a dictionary in [root] (the dictionaries' folder, maybe KOReader's): only its own files, never a folder with
     * anything else in it — its folder goes only when it is left empty.
     */
    fun delete(ifo: File, root: File) {
        val dir = ifo.parentFile ?: return
        val r = root.canonicalPath
        if (dir.canonicalPath != r && !dir.canonicalPath.startsWith(r + File.separator)) return
        val base = ifo.name.removeSuffix(".ifo")
        dir.listFiles()?.filter { f -> DICT_EXT.any { f.name == base + it } }?.forEach { it.delete() }
        if (dir.canonicalPath != r && dir.list()?.isEmpty() == true) dir.delete()
    }

    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "dict" }
}
