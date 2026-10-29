package io.github.kafeg.inkpeek

/**
 * Languages you learn (Languages → Translation), per answer language: a text already in the answer language is shown in
 * them — translation, their dictionary lines with the translated words' own grammar, AI about those versions. Pure logic.
 */
object Study {
    const val MAX = 2

    /** The stored list "de,en": ordered codes, at most [MAX]; "" or null — off. */
    fun parse(s: String?): List<String> = s.orEmpty().split(',').map { it.trim() }.filter { it.matches(Regex("[a-z]{2,3}")) }.distinct().take(MAX)

    fun format(langs: List<String>): String = langs.joinToString(",")

    /** The languages offered for [answer]: those with a pack [answer] → X (in the list or installed), by code. */
    fun offered(answer: String, pairs: Collection<Pair<String, String>>): List<String> =
        pairs.filter { it.first == answer && it.second != answer }.map { it.second }.distinct().sorted()

    /** Ticking [code] adds it at the end (not past [MAX]); unticking removes it. */
    fun toggle(langs: List<String>, code: String, on: Boolean): List<String> =
        if (!on) langs - code else if (code in langs || langs.size >= MAX) langs else langs + code

    fun full(langs: List<String>) = langs.size >= MAX

    /** What the card uses, in the stored order: the languages whose pack [answer] → X is there (installed and ticked). */
    fun effective(stored: List<String>, answer: String, pairs: Collection<Pair<String, String>>): List<String> =
        stored.filter { (answer to it) in pairs }

    /** The card's line under the title: "ru → de, en". */
    fun direction(answer: String, langs: List<String>): String = "$answer → ${langs.joinToString(", ")}"

    /** The Dictionary tab's page of a returned-to place "s:<n>" (a language per page); anything else — the first. */
    fun page(place: String?, count: Int): Int =
        place?.takeIf { it.startsWith("s:") }?.removePrefix("s:")?.toIntOrNull()?.takeIf { it in 0 until count } ?: 0

    /** A language's page title: "1 / 2 · German"; one language — its name alone. */
    fun pageTitle(at: Int, count: Int, name: String): String = (if (count > 1) "${at + 1} / $count · " else "") + name

    /** The translated words of a pack line's meaning ("Haus, Gebäude (alt), Dynastie"): each once, without notes in brackets. */
    fun words(meaning: String, max: Int = 5): List<String> =
        meaning.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), "").split(',', ';').map { it.trim().trim('.') }
            .filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(max)
}
