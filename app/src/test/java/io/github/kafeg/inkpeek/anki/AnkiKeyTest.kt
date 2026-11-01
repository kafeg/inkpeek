package io.github.kafeg.inkpeek.anki

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Key field as LexiPeek makes it (its tests/anki_test.cpp): one word from both apps is one Anki note. */
class AnkiKeyTest {
    @Test fun key() {
        val k = "ru-1a21f58f9570039d5e23f3619c8e9749" // sha256("haus")
        assertEquals(k, Anki.key("Haus", "ru"))
        assertEquals(k, Anki.key(" haus ", "RU"))
        assertEquals(k, Anki.key("Ha­us", "ru")) // a soft hyphen
        assertEquals("h-$k", Anki.key("Haus", "ru", history = true))
        assertEquals("en-1a21f58f9570039d5e23f3619c8e9749", Anki.key("Haus", "en"))
        assertEquals("ru-1033c70bf184184053d0837c822ebcc6", Anki.key("Ich mache die Tür zu!", "ru")) // NFD → NFC
        assertEquals(listOf("Front", "Back", "Audio", "Example", "Key"), Anki.FIELDS)
    }
}
