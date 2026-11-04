package io.github.kafeg.inkpeek

import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.stardict.Library
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.cleanPhrase
import io.github.kafeg.inkpeek.stardict.cleanQuery
import io.github.kafeg.inkpeek.stardict.spaced

/** How a selection becomes a card (docs/formats.md "Selection and lookup"); cases in tests/lookup. */
object Selection {
    /** [query]: looked up and stored; [title]: shown, and what translation and AI get. */
    data class Card(val kind: Kind, val title: String, val query: String)

    /** The text alone: a word as looked up, a phrase as selected. */
    fun of(raw: String): Card {
        val word = cleanQuery(raw)
        return if (Kind.of(word) == Kind.WORD) Card(Kind.WORD, word, word) else cleanPhrase(raw).let { Card(Kind.of(it), spaced(raw), it) }
    }

    /**
     * With the dictionaries (blocking): "die Konjugation" is the noun's word card ([nouns]: the packs to ask); a dashed
     * word no dictionary knows is the headword with spaces or its words one by one.
     */
    fun open(raw: String, lib: Library, answer: String, nouns: List<StarDict> = lib.all()): Card {
        val c = of(raw)
        if (c.kind == Kind.PHRASE && Kind.words(c.query) == 2) lib.articleNoun(c.query, answer, nouns)?.let { return Card(Kind.WORD, it, it) }
        if (c.kind != Kind.WORD) return c
        return when (val d = lib.dashed(c.query, answer)) {
            null -> c
            "" -> Card(Kind.PHRASE, spaced(raw), c.query)
            else -> Card(Kind.WORD, d, d)
        }
    }
}
