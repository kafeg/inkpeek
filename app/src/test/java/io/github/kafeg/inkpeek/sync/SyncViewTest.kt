package io.github.kafeg.inkpeek.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** The history stays on each device: another device's looks and deletes are not in the app's view; its saved words are. */
class SyncViewTest {
    private fun line(ms: Long, d: String, op: String, text: String, c: Long = 0) =
        Line(ms, 0, d, op, text, "word", "ru", "m", c = c).let { it.copy(raw = SyncLog.format(it)) }

    @Test fun ownHistory() {
        val me = "boox-7hq2mzta"; val mac = "mac-k3fw2xqa"
        val s = SyncLog.merge(SyncLog.view(listOf(
            line(1, mac, "saved.put", "Haus"), line(2, mac, "history.seen", "Haus", 3), line(3, me, "history.seen", "Haus", 1),
            line(4, mac, "history.seen", "Baum", 1), line(5, mac, "history.del", "Weg"), line(1, me, "history.seen", "Weg", 2)), me))
        assertEquals(listOf("Haus"), s.saved.map { it.text })
        assertEquals(mapOf("Haus" to 1L, "Weg" to 2L), s.history.associate { it.text to it.count })
        assertEquals(listOf(me), s.history.first { it.text == "Haus" }.devices)
    }
}
