package io.github.kafeg.inkpeek

import io.github.kafeg.inkpeek.ui.Koreader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreaderTest {
    @Test fun aNewFile() {
        val f = Koreader.withInkPeek(null)!!
        assertTrue(f.startsWith("return {\n") && "io.github.kafeg.inkpeek" in f && f.trimEnd().endsWith("}"))
    }

    @Test fun othersStay() {
        val old = "-- mine\nreturn {\n    { \"ColorDict\", \"ColorDict\", false, \"com.socialnmobile.colordict\", \"colordict\" },\n}\n"
        val new = Koreader.withInkPeek(old)!!
        assertTrue("com.socialnmobile.colordict" in new && "io.github.kafeg.inkpeek" in new && new.startsWith("-- mine\nreturn {"))
        assertEquals(old.length + 1 + new.lines().first { "inkpeek" in it }.length, new.length)
    }

    @Test fun alreadyThere() = assertNull(Koreader.withInkPeek("return {\n { \"InkPeek\", \"InkPeek\", false, \"io.github.kafeg.inkpeek\", \"text\" },\n}"))

    @Test(expected = IllegalStateException::class) fun notAList() { Koreader.withInkPeek("local x = 1") }
}
