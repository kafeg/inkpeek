package io.github.kafeg.inkpeek.stardict

// Other dictionaries (imported, downloaded, a KOReader folder) have no pack first line: their first meaning instead.

private val GRAMMAR = Regex("""class="grammar"[^>]*>([^<]+)<""")

/**
 * First meaning of an article as one short plain line: the first sense of the first numbered list, else the first content
 * line. [pos] ("verb", "noun") picks that part of speech where the article marks several (WikDict: go — noun, verb).
 */
fun shortMeaning(html: String, headword: String, pos: String? = null): String {
    if (html.contains("class=\"grammar\"")) {
        val marks = GRAMMAR.findAll(html).toList()
        val m = marks.firstOrNull { pos != null && it.groupValues[1].contains(pos, true) } ?: marks.firstOrNull()
        val part = if (m == null) html else html.substring(m.range.last, marks.firstOrNull { it.range.first > m.range.last }?.range?.first ?: html.length)
        Regex("<div>([^<]+)</div>").findAll(part).map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }.distinct().take(3).joinToString(", ").takeIf { it.isNotEmpty() }?.let { return it.take(90) }
    }
    fun plain(h: String) = h.replace(Regex("(?i)<li[^>]*>"), "\n• ").replace(Regex("(?i)<br\\s*/?>|</(p|li|div|h[1-6])>"), "\n").replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
    // Mueller-style marks: "_v." (part of speech), "1>" (sense number).
    fun clean(s: String) = s.substringBefore('◆').replace(Regex("^(•|\\d+[.)]|[-–])\\s*"), "").replace(Regex("_\\p{L}+\\.?\\s*"), "")
        .replace(Regex("(^|\\s)\\d+>\\s*"), " ").replace(Regex("\\s+"), " ").trim()
        .replace(Regex("^\\([^)]*\\)\\s*"), "").substringBefore(';').trim().take(90) // first sense only; "(full form)" is a note
    // Wiktionary and most HTML dictionaries keep senses in <ol><li>; transcription and forms come before it in <ul>.
    Regex("(?is)<ol[^>]*>.*?<li[^>]*>(.*?)(</li>|<ol|<ul)").findAll(html).map { clean(plain(it.groupValues[1])) }
        .firstOrNull { it.length > 1 }?.let { return it }
    // A leading [transcription] is not a sense (Mueller: "[went] _p. от go 1").
    val lines = plain(html).lines().map { it.trim().replace(Regex("^\\[[^]]*]\\s*"), "") }.filter { it.length > 1 }
    val skip = Regex("^(•\\s*)?(IPA|Hyphenation|Rhymes|Audio|Homophone|Anagrams|Etymology|МФА|Произношение|Семантические|Морфолог)|IPA:|МФА", RegexOption.IGNORE_CASE)
    val oneWord = Regex("^[\\p{L} ]{1,20}$") // part-of-speech headings: Noun, Verb, Существительное
    val sense = lines.firstOrNull { Regex("^(•|\\d+[.)]|[-–]) ?\\S").containsMatchIn(it) && !skip.containsMatchIn(it) && it.length > 3 }
        ?: lines.firstOrNull { !it.equals(headword, true) && !skip.containsMatchIn(it) && !it.startsWith("[") && !oneWord.matches(it) }
        ?: ""
    return clean(sense)
}
