package io.github.kafeg.inkpeek

/**
 * The language you learn (Languages → Translation), per answer language: a text already in the answer language is shown in
 * it — translation, its dictionary line with the translated words' own grammar, AI about that version. Pure logic.
 */
object Study {
    /** The stored code "de"; "" or null — off. An older list "de,en": its first language. */
    fun parse(s: String?): String? = s.orEmpty().split(',').map { it.trim() }.firstOrNull { it.matches(Regex("[a-z]{2,3}")) }

    /** The languages offered for [answer]: those with a pack [answer] → X (in the list or installed), by code. */
    fun offered(answer: String, pairs: Collection<Pair<String, String>>): List<String> =
        pairs.filter { it.first == answer && it.second != answer }.map { it.second }.distinct().sorted()

    /** What the card uses: [stored] when its pack [answer] → it is there (installed and ticked), else null. */
    fun effective(stored: String?, answer: String, pairs: Collection<Pair<String, String>>): String? =
        stored?.takeIf { (answer to it) in pairs }

    /** The card's line under the title: "ru → de". */
    fun direction(answer: String, lang: String): String = "$answer → $lang"

    /** The translated words of a pack line's meaning ("Haus, Gebäude (alt), Dynastie"): each once, without notes in brackets. */
    fun words(meaning: String, max: Int = 5): List<String> =
        meaning.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), "").split(',', ';').map { it.trim().trim('.') }
            .filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(max)
}
