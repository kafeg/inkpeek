package io.github.kafeg.inkpeek.ai

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AskKeysTest {
    @After fun reset() { Ai.picked = { null } }

    @Test fun defaultsAskTheOriginalKeys() {
        assertEquals(listOf("grammar", "words"), Ask.ANALYSIS.active.map { it.name })
        assertEquals(listOf("explanation", "examples"), Ask.CONTEXT.active.map { it.name })
    }

    @Test fun pickedKeysChangePromptSchemaAndVersion() {
        val before = Ask.ANALYSIS.version
        Ai.picked = { if (it == "analysis.related") true else if (it == "analysis.grammar") false else null }
        assertEquals(listOf("words", "related"), Ask.ANALYSIS.active.map { it.name })
        assertTrue("- related:" in Ask.ANALYSIS.prompt() && "- grammar:" !in Ask.ANALYSIS.prompt())
        assertEquals(listOf("words", "related"), Ask.ANALYSIS.schema()["required"])
        assertNotEquals(before, Ask.ANALYSIS.version)
    }

    @Test fun noneLeftFallsBackToDefaults() {
        Ai.picked = { false }
        assertEquals(listOf("grammar", "words"), Ask.ANALYSIS.active.map { it.name })
    }
}
