package io.github.kafeg.inkpeek

import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.Ask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTest {
    @Test fun parse() {
        assertEquals("de", Study.parse("de"))
        assertEquals("de", Study.parse(" de "))
        assertNull(Study.parse(""))
        assertNull(Study.parse(null))
        assertEquals("en", Study.parse("en,de")) // an older list of two: its first language only
        assertEquals("de", Study.parse(",Bad,de,1"))
    }

    @Test fun offered() {
        val pairs = listOf("ru" to "de", "ru" to "en", "de" to "ru", "en" to "ru", "ru" to "en")
        assertEquals(listOf("de", "en"), Study.offered("ru", pairs))
        assertEquals(listOf("ru"), Study.offered("de", pairs))
        assertEquals(emptyList<String>(), Study.offered("fr", pairs))
        assertEquals(emptyList<String>(), Study.offered("ru", listOf("ru" to "ru")))
    }

    @Test fun effective() {
        val pairs = listOf("ru" to "de", "ru" to "en", "de" to "ru")
        assertEquals("en", Study.effective("en", "ru", pairs))
        assertNull(Study.effective("fr", "ru", pairs)) // no pack ru → fr: not in the card
        assertNull(Study.effective("ru", "de", listOf("ru" to "de")))
        assertNull(Study.effective(null, "ru", pairs))
    }

    @Test fun direction() {
        assertEquals("ru → de", Study.direction("ru", "de"))
    }

    @Test fun words() {
        assertEquals(listOf("Haus", "Gebäude", "Dynastie"), Study.words("Haus, Gebäude, Dynastie"))
        assertEquals(listOf("machen", "tun"), Study.words("machen, tun"))
        assertEquals(listOf("schön", "hübsch"), Study.words("schön (bildlich), hübsch; Schön"))
        assertEquals(emptyList<String>(), Study.words(""))
        assertEquals(2, Study.words("a, b, c", max = 2).size)
    }

    @Test fun aiVersion() {
        // Without a language you learn: the prompt and version as before (saved answers stay); with one: per language.
        assertEquals(Ask.QUICK.version, Ask.QUICK.version(null))
        assertEquals("", Ai.study(null))
        assertNotEquals(Ask.QUICK.version, Ask.QUICK.version("German"))
        assertNotEquals(Ask.QUICK.version("German"), Ask.QUICK.version("English"))
        assertTrue(Ai.study("German").contains("learns German"))
        assertFalse(Ai.study("German").contains("{study}"))
    }
}
