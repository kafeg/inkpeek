package io.github.kafeg.inkpeek.stardict

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Archives and loose files into one folder per dictionary, named by its bookname. */
class ImporterTest {
    private val files = mapOf("x/demo.ifo" to "StarDict's dict ifo file\nversion=2.4.2\nbookname=Demo: de-ru\nwordcount=0\n",
        "x/demo.idx" to "", "x/demo.dict" to "", "x/readme.txt" to "-", "../evil.ifo" to "bookname=evil\n")

    private fun zip() = ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { z ->
        files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } } }.toByteArray()

    private fun tgz() = ByteArrayOutputStream().also { b -> TarArchiveOutputStream(GZIPOutputStream(b)).use { t ->
        files.forEach { (n, s) -> val d = s.toByteArray(); t.putArchiveEntry(TarArchiveEntry(n).apply { size = d.size.toLong() }); t.write(d); t.closeArchiveEntry() } } }.toByteArray()

    @Test fun archives() {
        listOf("demo.zip" to zip(), "demo.tar.gz" to tgz()).forEach { (name, bytes) ->
            val target = Files.createTempDirectory("dicts").toFile(); val tmp = Files.createTempDirectory("tmp").toFile()
            val dirs = Importer.importStream(name, bytes.inputStream(), target, tmp)
            assertEquals(listOf("Demo_ de-ru"), dirs.map { it.name })
            assertEquals(listOf("demo.dict", "demo.idx", "demo.ifo"), dirs.single().list()!!.sorted())
            assertEquals(listOf("Demo_ de-ru"), target.list()!!.toList()) // nothing from outside the archive's folder
            // The same dictionary again gets its own folder.
            assertEquals("Demo_ de-ru-2", Importer.importStream(name, bytes.inputStream(), target, tmp).single().name)
        }
    }

    @Test fun looseFilesAndRefusals() {
        val target = Files.createTempDirectory("dicts").toFile(); val tmp = Files.createTempDirectory("tmp").toFile()
        assertEquals(1, Importer.importStream("demo.ifo", files.getValue("x/demo.ifo").byteInputStream(), target, tmp).size)
        assertEquals(true, runCatching { Importer.importStream("book.epub", "x".byteInputStream(), target, tmp) }.isFailure)
        assertEquals(true, Importer.isDictFile("A.DICT.DZ"))
        assertEquals(false, File(tmp.parentFile, "evil.ifo").exists())
    }
}

/** Deleting from a shared folder (KOReader's) never takes other dictionaries with it. */
class DeleteTest {
    @Test fun ownFolderOrOnlyItsFiles() {
        val root = Files.createTempDirectory("dict").toFile()
        File(root, "a").mkdirs(); listOf("a/x.ifo", "a/x.idx", "y.ifo", "y.dict.dz", "z.ifo", "z.idx").forEach { File(root, it).writeText("-") }
        Importer.delete(File(root, "y.ifo"), root)
        assertEquals(listOf("a", "z.idx", "z.ifo"), root.list()!!.sorted())
        Importer.delete(File(root, "a/x.ifo"), root)
        assertEquals(listOf("z.idx", "z.ifo"), root.list()!!.sorted())
        Importer.delete(File(root.parentFile, "elsewhere/q.ifo"), root) // outside the folder: nothing
        assertEquals(true, root.exists())
        // A dictionary next to others in a subfolder (KOReader's folder inside a broad one): the others stay.
        File(root, "dict").mkdirs(); listOf("dict/p.ifo", "dict/p.idx", "dict/q.ifo", "dict/q.idx").forEach { File(root, it).writeText("-") }
        Importer.delete(File(root, "dict/p.ifo"), root)
        assertEquals(listOf("q.idx", "q.ifo"), File(root, "dict").list()!!.sorted())
    }
}
