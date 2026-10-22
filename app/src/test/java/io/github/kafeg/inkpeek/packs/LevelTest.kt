package io.github.kafeg.inkpeek.packs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelTest {
    private fun dict(x: String, y: String, cover: Double) = Component("dict-$x-$y", "dict", x, y, "", 0, "", coverage = cover)
    private fun model(x: String, y: String) = Component("model-$x-$y", "model", x, y, "", 0, "")

    @Test fun labels() {
        val cs = listOf(dict("de", "ru", 92.0), dict("uk", "ru", 90.0), dict("be", "en", 55.0), dict("nl", "en", 70.0), dict("pl", "ru", 0.0),
            model("de", "en"), model("en", "ru"), model("nl", "en"))
        assertEquals(Level.FULL, Packs.level(cs, "de" to "ru"))
        assertEquals(Level.BASIC, Packs.level(cs, "uk" to "ru"))   // no uk→en model: no offline translation
        assertEquals(Level.BASIC, Packs.level(cs, "nl" to "en"))
        assertEquals(Level.EXPERIMENTAL, Packs.level(cs, "be" to "en"))
        assertNull(Packs.level(cs, "pl" to "ru"))
    }
}
