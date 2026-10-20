package io.github.kafeg.inkpeek.stardict

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Manual check against real dictionaries: INKPEEK_DICTS=/path/to/dicts ./gradlew test --tests '*RealDicts*' -i */
class RealDictsTest {
    @Test fun gloss() {
        val root = System.getenv("INKPEEK_DICTS")?.let(::File)
        assumeTrue(root?.isDirectory == true)
        val lib = Library(listOf(root!!), File(root.parentFile, "dicts-work"))
        lib.all().forEach { it.prepare() }
        val text = System.getenv("INKPEEK_TEXT") ?: "Als Gregor Samsa eines Morgens aus unruhigen Träumen erwachte, fand er sich in seinem Bett zu einem ungeheueren Ungeziefer verwandelt."
        Regex("[\\p{L}][\\p{L}'’-]*").findAll(text).map { it.value }.forEach { w ->
            println("HITS $w: " + lib.lookup(w, emptyList(), emptySet()).joinToString { (d, h) -> d.name + "=" + h.first().word })
        }
        lib.gloss(text, emptyList(), emptySet(), Character.UnicodeScript.CYRILLIC).forEach { println("GLOSS ${it.word} -> ${it.base} — ${it.meaning}") }
    }
}
