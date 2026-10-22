package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/** Binary search over the .rel lines in Python's (code point) order: every word found, absent ones not. */
class RelatedTest {
    @Test fun findsEveryWord() {
        val words = (listOf("Haus", "Häuser", "machen", "zumachen", "дом", "делать", "a", "Zug") + (1..300).map { "w%04d".format(it) }).sorted()
        val f = Files.createTempFile("x", ".rel").toFile()
        f.writeText(words.sortedBy { it.codePoints().toArray().joinToString(",") { c -> "%06x".format(c) } }
            .joinToString("\n", postfix = "\n") { w -> "$w\t${w}1|${w}2\tsyn\t\texpr one|expr two\t[ipa]\torigin of $w\tf1:present|f2:past" })
        val r = Related(f)
        words.forEach { w -> assertEquals(w, listOf("${w}1", "${w}2"), r.of(w)?.rel) }
        listOf("", "Hau", "machenx", "дома", "zz", "A").forEach { assertNull(it, r.of(it)) }
        val e = r.of("machen")!!
        assertEquals(listOf("expr one", "expr two"), e.expr)
        assertEquals("origin of machen", e.origin)
        assertEquals(listOf("f1" to "present", "f2" to "past"), e.forms)
        assertEquals(emptyList<String>(), e.ant)
    }
}
