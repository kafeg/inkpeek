package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Separable verbs: the device's "sep" cases (androidTest/assets/cases.tsv) on the data of the built German pack,
 * made by scripts/separable-fixture.py (test resources). Each case: the verbs found, or "(none)"; a known gap of the
 * data ("~") is printed, not failed.
 */
class SeparableTest {
    private val forms = HashMap<String, List<String>>()
    private val particles = HashMap<String, String>()
    private val cases = ArrayList<Pair<String, String>>()
    private val gaps = ArrayList<Pair<String, String>>()
    init {
        javaClass.classLoader!!.getResourceAsStream("separable-de.tsv")!!.bufferedReader().forEachLine { l ->
            if (l.startsWith("#") || l.isBlank()) return@forEachLine
            val (a, b) = l.split('\t')
            when { a.startsWith("?") -> cases += a.drop(1) to b; a.startsWith("~") -> gaps += a.drop(1) to b
                a.startsWith("=") -> particles[a.drop(1)] = b; else -> forms[a] = b.split('|') }
        }
    }

    private fun verbs(s: String) = Separable.find(s, { forms[it].orEmpty() }, { particles[it] }).joinToString(", ") { it.verb }.ifEmpty { "(none)" }

    @Test fun cases() {
        assertEquals(cases.joinToString("\n") { "${it.first} → ${it.second}" }, cases.joinToString("\n") { "${it.first} → ${verbs(it.first)}" })
        gaps.forEach { (s, e) -> verbs(s).let { if (it != e) println("GAP $s → $it (want $e)") } }
    }
}
