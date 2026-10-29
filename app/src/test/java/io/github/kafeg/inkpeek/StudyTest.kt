package io.github.kafeg.inkpeek

import io.github.kafeg.inkpeek.ai.Ask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTest {
    @Test fun parse() {
        assertEquals(listOf("de", "en"), Study.parse("de,en"))
        assertEquals(listOf("en", "de"), Study.parse(" en , de "))
        assertEquals(emptyList<String>(), Study.parse(""))
        assertEquals(emptyList<String>(), Study.parse(null))
        assertEquals(listOf("de", "en"), Study.parse("de,de,en,fr")) // two at most, each once
        assertEquals(listOf("de"), Study.parse("de,,Bad,1"))
        assertEquals("de,en", Study.format(listOf("de", "en")))
    }

    @Test fun offered() {
        val pairs = listOf("ru" to "de", "ru" to "en", "de" to "ru", "en" to "ru", "ru" to "en")
        assertEquals(listOf("de", "en"), Study.offered("ru", pairs))
        assertEquals(listOf("ru"), Study.offered("de", pairs))
        assertEquals(emptyList<String>(), Study.offered("fr", pairs))
        assertEquals(emptyList<String>(), Study.offered("ru", listOf("ru" to "ru")))
    }

    @Test fun twoAtMost() {
        var l = Study.toggle(emptyList(), "en", true)
        l = Study.toggle(l, "de", true)
        assertEquals(listOf("en", "de"), l) // the order of ticking
        assertTrue(Study.full(l))
        assertEquals(listOf("en", "de"), Study.toggle(l, "fr", true))
        assertEquals(listOf("de"), Study.toggle(l, "en", false))
        assertEquals(listOf("de", "fr"), Study.toggle(Study.toggle(l, "en", false), "fr", true))
        assertEquals(listOf("en", "de"), Study.toggle(l, "de", true))
    }

    @Test fun effectiveKeepsOrder() {
        val pairs = listOf("ru" to "de", "ru" to "en", "de" to "ru")
        assertEquals(listOf("en", "de"), Study.effective(listOf("en", "de"), "ru", pairs))
        assertEquals(listOf("de"), Study.effective(listOf("fr", "de"), "ru", pairs)) // no pack ru → fr: not in the card
        assertEquals(emptyList<String>(), Study.effective(listOf("ru"), "de", listOf("ru" to "de")))
    }

    @Test fun direction() {
        assertEquals("ru → de, en", Study.direction("ru", listOf("de", "en")))
        assertEquals("en → de", Study.direction("en", listOf("de")))
    }

    @Test fun aLanguagePerPage() {
        assertEquals(1, Study.page("s:1", 2)) // ← from page 2's article or a word's card: page 2 again
        assertEquals(0, Study.page("s:0", 2))
        assertEquals(0, Study.page("s:2", 2)) // a language gone from the list: the first
        assertEquals(0, Study.page("a:x", 2))
        assertEquals(0, Study.page(null, 1))
        assertEquals(0, Study.page("s:bad", 2))
        assertEquals("1 / 2 · German", Study.pageTitle(0, 2, "German"))
        assertEquals("2 / 2 · English", Study.pageTitle(1, 2, "English"))
        assertEquals("German", Study.pageTitle(0, 1, "German")) // one language: no pager
    }

    @Test fun words() {
        assertEquals(listOf("Haus", "Gebäude", "Dynastie"), Study.words("Haus, Gebäude, Dynastie"))
        assertEquals(listOf("machen", "tun"), Study.words("machen, tun"))
        assertEquals(listOf("schön", "hübsch"), Study.words("schön (bildlich), hübsch; Schön"))
        assertEquals(emptyList<String>(), Study.words(""))
        assertEquals(2, Study.words("a, b, c", max = 2).size)
    }

    @Test fun aiVersion() {
        // Without languages you learn: the version as before (saved answers stay); with them: per list and order.
        assertEquals(Ask.QUICK.version, Ask.QUICK.version(emptyList()))
        assertNotEquals(Ask.QUICK.version, Ask.QUICK.version(listOf("German")))
        assertNotEquals(Ask.QUICK.version(listOf("German", "English")), Ask.QUICK.version(listOf("English", "German")))
    }
}
