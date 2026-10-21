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
