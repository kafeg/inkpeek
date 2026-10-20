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
            val packs = Packs(File(home, "InkPeek/packs"), File(home, "InkPeek/models"), "http://127.0.0.1:${ss.localPort}/")
            packs.install(Component("dict-de-ru", "dict", "de", "ru", "dict-de-ru.zip", zip.size.toLong(), sha))
            assertTrue(File(packs.dicts, "dict-de-ru/dict-de-ru.ifo").exists())
            assertEquals(mapOf("dict-de-ru" to sha), packs.installed())
            assertTrue(!File(packs.root, "tmp").exists())
        }
        home.deleteRecursively()
    }
}
