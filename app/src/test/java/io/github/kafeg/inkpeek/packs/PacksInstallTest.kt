package io.github.kafeg.inkpeek.packs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/** Installing a pack on a fresh device: no packs/, dicts/ or models/ folders yet. */
class PacksInstallTest {
    /** Serves [body] to every request on a local port. */
    private fun serve(body: ByteArray) = ServerSocket(0).also { ss ->
        thread(isDaemon = true) {
            while (!ss.isClosed) runCatching { ss.accept().use { s ->
                val r = s.getInputStream().bufferedReader(); while (r.readLine()?.isNotEmpty() == true) Unit
                s.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(body); flush() }
            } }
        }
    }

    @Test fun dictionaryIntoAFreshFolder() {
        val zip = ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { z ->
            z.putNextEntry(ZipEntry("dict-de-ru/dict-de-ru.ifo")); z.write("StarDict's dict ifo file\n".toByteArray()); z.closeEntry()
        } }.toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(zip).joinToString("") { "%02x".format(it) }
        val home = Files.createTempDirectory("fresh").toFile()
        serve(zip).use { ss ->
            val packs = Packs(File(home, "InkPeek/packs"), File(home, "InkPeek/models"), "http://127.0.0.1:${ss.localPort}/", okhttp3.OkHttpClient())
            packs.install(Component("dict-de-ru", "dict", "de", "ru", "dict-de-ru.zip", zip.size.toLong(), sha))
            assertTrue(File(packs.dicts, "dict-de-ru/dict-de-ru.ifo").exists())
            assertEquals(mapOf("dict-de-ru" to sha), packs.installed())
            assertTrue(!File(packs.root, "tmp").exists())
        }
        home.deleteRecursively()
    }
}

/** Updates and failures: the old version stays whole until the new one is checked and in place; nothing half is left. */
class PacksUpdateTest {
    private fun zip(vararg files: Pair<String, String>) = ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { z ->
        files.forEach { (n, t) -> z.putNextEntry(ZipEntry("dict-de-ru/$n")); z.write(t.toByteArray()); z.closeEntry() }
    } }.toByteArray()
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun dict(body: ByteArray, sha: String = sha(body)) = Component("dict-de-ru", "dict", "de", "ru", "dict-de-ru.zip", body.size.toLong(), sha)

    /** What the local server sends now. */
    @Volatile private var body = ByteArray(0)

    /** Packs in a fresh home, downloading from a local server that sends [body]. */
    private fun withPacks(run: (Packs) -> Unit) {
        val home = Files.createTempDirectory("upd").toFile()
        java.net.ServerSocket(0).use { ss ->
            thread(isDaemon = true) {
                while (!ss.isClosed) runCatching { ss.accept().use { s ->
                    val r = s.getInputStream().bufferedReader(); while (r.readLine()?.isNotEmpty() == true) Unit
                    val b = body
                    s.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(b); flush() }
                } }
            }
            run(Packs(File(home, "packs"), File(home, "models"), "http://127.0.0.1:${ss.localPort}/", okhttp3.OkHttpClient()))
        }
        home.deleteRecursively()
    }

    @Test fun aBadDownloadLeavesTheOldVersion() = withPacks { p ->
        val v1 = zip("dict-de-ru.ifo" to "v1"); body = v1; p.install(dict(v1))
        // The newer file with a wrong checksum: refused, the old dictionary and its record as they were.
        val v2 = zip("dict-de-ru.ifo" to "v2"); body = v2
        assertTrue(runCatching { p.install(dict(v2, "0".repeat(64))) }.isFailure)
        assertEquals("v1", File(p.dicts, "dict-de-ru/dict-de-ru.ifo").readText())
        assertEquals(sha(v1), p.installed()["dict-de-ru"])
        assertTrue(p.root.listFiles()!!.none { it.name.endsWith(".part") } && !File(p.root, "tmp").exists())
    }

    @Test fun anUpdateReplacesTheWholeFolder() = withPacks { p ->
        val v1 = zip("dict-de-ru.ifo" to "v1", "old.txt" to "gone"); body = v1; p.install(dict(v1))
        val v2 = zip("dict-de-ru.ifo" to "v2"); body = v2; p.install(dict(v2))
        assertEquals(listOf("dict-de-ru.ifo"), File(p.dicts, "dict-de-ru").list()!!.toList())
        assertEquals("v2", File(p.dicts, "dict-de-ru/dict-de-ru.ifo").readText())
        assertEquals(sha(v2), p.installed()["dict-de-ru"])
    }

    @Test fun aDownloadCutShortIsCleanedUpAndBadIdsAreRefused() = withPacks { p ->
        p.root.mkdirs(); File(p.root, "model-xx.tar.zst.part").writeText("half")
        val v1 = zip("dict-de-ru.ifo" to "v1"); body = v1; p.install(dict(v1))
        assertTrue(p.root.listFiles()!!.none { it.name.endsWith(".part") })
        assertTrue(runCatching { p.install(Component("../evil", "dict", "de", "ru", "x.zip", 1, "0")) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { p.install(Component("model-x", "model", "", "", "x.tar.zst", 1, "0")) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { p.remove(Component("model-x", "model", "..", "ru", "x", 1, "0")) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(File(p.dicts, "dict-de-ru/dict-de-ru.ifo").exists())
    }
}

/** Removing a pair frees only what no other installed pair needs; updates are the parts with a newer file. */
class PacksPlanTest {
    private fun c(id: String, kind: String, from: String = "", to: String = "", sha: String = "1") = Component(id, kind, from, to, "$id.zip", 1, sha)
    private val cs = listOf(c("dict-de-ru", "dict", "de", "ru"), c("dict-en-ru", "dict", "en", "ru"), c("model-de-en", "model", "de", "en"),
        c("model-en-ru", "model", "en", "ru"), c("langid", "langid"))

    @Test fun freedAndUpdates() {
        val both = listOf("de" to "ru", "en" to "ru")
        // de→ru goes through English: en→ru's model stays for the other pair.
        org.junit.Assert.assertEquals(listOf("dict-de-ru", "model-de-en"), Packs.freed(cs, both, "de" to "ru").map { it.id })
        org.junit.Assert.assertEquals(listOf("dict-en-ru", "model-en-ru", "langid"), Packs.freed(cs, listOf("en" to "ru"), "en" to "ru").map { it.id })
        org.junit.Assert.assertEquals(listOf("langid"), Packs.updates(cs, mapOf("dict-de-ru" to "1", "langid" to "0")).map { it.id })
    }
}
