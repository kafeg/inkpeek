package io.github.kafeg.inkpeek

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every app language has every string of the English one (a missing key would show English in the middle of it). */
class StringsTest {
    private fun keys(f: File) = Regex("""<string name="([^"]+)"(?![^>]*translatable="false")""").findAll(f.readText()).map { it.groupValues[1] }.toSet()

    @Test fun everyLanguageHasEveryString() {
        val res = File("src/main/res")
        val en = keys(File(res, "values/strings.xml"))
        res.listFiles { f -> f.name.startsWith("values-") }!!.map { File(it, "strings.xml") }.filter { it.exists() }.forEach { f ->
            assertEquals("${f.parentFile.name}: missing", emptySet<String>(), en - keys(f))
            assertEquals("${f.parentFile.name}: not in English", emptySet<String>(), keys(f) - en)
        }
    }
}
