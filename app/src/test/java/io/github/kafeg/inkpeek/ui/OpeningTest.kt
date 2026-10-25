package io.github.kafeg.inkpeek.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Card → Opening: only what can open is offered; a choice gone or not answerable now falls back. */
class OpeningTest {
    @Test fun choicesFollowTheSettings() {
        assertEquals(listOf("offline", "ai", "dict", "analysis", "context"), Opening.choices(true, true, listOf("analysis", "context")))
        assertEquals(listOf("dict"), Opening.choices(false, false, emptyList()))
        assertEquals(listOf("offline", "dict", "context"), Opening.choices(true, false, listOf("context")))
    }

    @Test fun aChoiceGoneOrBlockedFallsBack() {
        val all = Opening.choices(true, true, listOf("analysis"))
        assertEquals("ai", Opening.pick("ai", all))
        assertEquals("offline", Opening.pick("ai", all) { it == "ai" })          // no network: the offline translation
        assertEquals("offline", Opening.pick("context", all))                   // Context's module is off
        assertEquals("dict", Opening.pick("analysis", Opening.choices(false, true, emptyList()))) // no offline translation either
        assertEquals("dict", Opening.pick("offline", Opening.choices(false, false, emptyList()))) // method Off
        assertEquals("dict", Opening.pick("dict", all) { true })                 // word by word is never blocked
    }
}
