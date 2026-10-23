package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Test

/** Separable verbs on sentences from de.wiktionary's examples; the data is a slice of de.wiktionary (test resources). */
class SeparableTest {
    private val forms = HashMap<String, List<String>>()
    private val particles = HashMap<String, String>()
    init {
        javaClass.classLoader!!.getResourceAsStream("separable-de.tsv")!!.bufferedReader().forEachLine { l ->
            if (l.startsWith("#") || l.isBlank()) return@forEachLine
            val (a, b) = l.split('\t')
            if (a.startsWith("=")) particles[a.drop(1)] = b else forms[a] = b.split('|')
        }
    }

    private fun verbs(s: String) = Separable.find(s, { forms[it].orEmpty() }, { particles[it] }).map { it.verb }

    @Test fun particleAtTheEndOfItsClause() {
        assertEquals(listOf("zumachen"), verbs("Ich mache die Tür zu."))
        assertEquals(listOf("aufhören"), verbs("Sie hörte nicht auf."))
        assertEquals(listOf("aufstehen"), verbs("Sie steht jeden Morgen um sechs Uhr auf."))
        assertEquals(listOf("vorbeifahren"), verbs("Fährst du heute Abend noch bei deiner Mutter vorbei?"))
        assertEquals(listOf("austauschen"), verbs("Tauschen Sie sich über dieses Thema aus!"))
    }

    @Test fun aClauseOrAConjunctionAfterTheParticle() {
        assertEquals(listOf("anrufen"), verbs("Ich rufe dich morgen an, wenn ich zu Hause bin."))
        assertEquals(listOf("herausstellen"), verbs("Die Himmelsboten, so stellte sich später heraus, waren Meteoriten vom Mars."))
        assertEquals(listOf("ankommen"), verbs("Er kam spät an und ging schlafen."))
        assertEquals(listOf("anhaben", "aufhaben"), verbs("Er hat einen braunen Burnus an und eine ungeheure Pelzmütze auf, wie ich sie bei Hirten gesehen habe."))
        assertEquals(listOf("auffüllen", "austrinken"), verbs("Bodo füllte das Glas mit Wasser auf und trank es mit einem Zug aus."))
    }

    @Test fun wordsBetweenThatLookLikeVerbs() {
        assertEquals(listOf("ausgeben"), verbs("Er gab sein letztes Geld für Süßigkeiten aus."))      // sein: not the verb
        assertEquals(listOf("ausdrücken"), verbs("Er drückte ihnen sein Beileid aus."))
        assertEquals(listOf("einschätzen"), verbs("Ich schätze meine Kollegen als sehr zuverlässig ein.")) // meine: not meinen
        assertEquals(listOf("einteilen"), verbs("Franz teilt sich sein Taschengeld ein."))
        assertEquals(listOf("zusammenkrümmen"), verbs("Wenn man die Maden mit Wasser besprüht, krümmen sie sich zusammen."))
    }

    @Test fun nounsAreNotVerbs() {
        assertEquals(listOf("auszupfen"), verbs("Ida zupfte die Spitzen der Fliege aus."))          // die Fliege, not fliegen
        assertEquals(listOf("aufhängen", "einpuppen"), verbs("Die Raupe hängt sich an einem Faden auf und puppt sich im Hängen ein."))
    }

    @Test fun nothingWhereNoVerbIsApart() {
        assertEquals(emptyList<String>(), verbs("Ich mache das für dich zu Hause."))   // zu does not close its clause
        assertEquals(emptyList<String>(), verbs("Wir durchfuhren einen Tunnel."))
        assertEquals(emptyList<String>(), verbs("Er kommt aus Berlin."))
    }
}
