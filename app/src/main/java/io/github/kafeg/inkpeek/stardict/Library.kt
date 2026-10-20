package io.github.kafeg.inkpeek.stardict

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Dictionaries under [roots]. Indexes are built by one background thread, one dictionary at a time;
 * [status] tells the UI what is going on. Order and the disabled set are kept by the caller as ids.
 */
class Library(private val roots: List<File>, private val workRoot: File) {
    private var cache: Map<String, StarDict> = emptyMap()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "inkpeek-index").apply { isDaemon = true } }
    private val queued = ConcurrentHashMap.newKeySet<String>()
    /** id -> "indexing" | "error: …"; absent = ready or not yet looked at. */
    val status = ConcurrentHashMap<String, String>()

    @Synchronized fun all(order: List<String> = emptyList()): List<StarDict> {
        val found = roots.flatMap { StarDict.scan(it) }.map { it.canonicalFile }.distinctBy { it.path }
        val byPath = cache.values.associateBy { it.ifo.path }
        cache = found.map { byPath[it.path] ?: StarDict(it, workRoot) }.associateBy { it.id }
        // Drop the index of a dictionary that is gone from disk (not merely unreadable right now).
        workRoot.listFiles()?.filter { it.name !in cache }?.forEach { dir ->
            val src = File(dir, "source").takeIf { it.exists() }?.readText()
            if (src == null || (roots.any { it.isDirectory } && !File(src).exists())) dir.deleteRecursively()
        }
        val rank = order.withIndex().associate { it.value to it.index }
        // Roots first (the language packs before the user's own), then the user's order within a root.
        val root = { d: StarDict -> roots.indexOfFirst { d.ifo.path.startsWith(it.canonicalPath + File.separator) }.let { if (it < 0) roots.size else it } }
        return cache.values.sortedWith(compareBy({ root(it) }, { rank[it.id] ?: Int.MAX_VALUE }, { it.name }))
    }

    /** Queues index building for every dictionary that needs it. */
    fun prepareAll() = cache.values.filter { !it.ready }.forEach { prepareAsync(it) }

    fun prepareAsync(d: StarDict) {
        // A failed index is not retried on every lookup: the error stays in [status] until the app restarts.
        if (d.ready || status[d.id]?.startsWith("error") == true || !queued.add(d.id)) return
        status[d.id] = "indexing"
        worker.execute {
            runCatching { d.prepare() }.fold({ status.remove(d.id) }, { status[d.id] = "error: ${it.message ?: it.javaClass.simpleName}" })
            queued.remove(d.id)
        }
    }

    val busy: Boolean get() = queued.isNotEmpty()

    /**
     * Word-by-word help for a phrase or passage: each distinct word with its base form and first meaning.
     * Only dictionaries that know most of the text count (so an English dictionary does not explain German
     * "aus" as "AU"); among them, one whose meanings are in [script] (the answer language) wins, e.g. de→ru over de→en,
     * then the one that knows more words of this text.
     */
    fun gloss(text: String, order: List<String>, disabled: Set<String>, script: Character.UnicodeScript? = null, limit: Int = 60): List<Gloss> {
        val words = Regex("[\\p{L}][\\p{L}'’-]*").findAll(text).map { it.value }.distinctBy { it.lowercase() }.take(limit).toList()
        val found = words.associateWith { lookup(it, order, disabled) }
        val known = found.values.flatMap { r -> r.map { it.first.id } }.groupingBy { it }.eachCount()
        val top = known.values.maxOrNull() ?: 0
        return words.map { w ->
            // Meanings in the answer script first, then the dictionary that knows more of this text (de→ru over en→ru
            // for a German sentence, even when en→ru comes first in the order).
            val pick = found.getValue(w).filter { (d, _) -> (known[d.id] ?: 0) * 2 >= top }
                .map { (d, hits) -> Triple(d, hits.first(), runCatching { shortMeaning(hits.first().html, hits.first().word) }.getOrDefault("")) }
                .filter { it.third.isNotBlank() }
                .sortedWith(compareBy({ script == null || scriptOf(it.third) != script }, { -(known[it.first.id] ?: 0) }))
                .firstOrNull()
            pick?.let { (_, h, m) -> Gloss(w, h.word, m) } ?: Gloss(w, w, "")
        }
    }

    /** Main letter script of a text (CYRILLIC, LATIN, HAN…). */
    fun scriptOf(s: String): Character.UnicodeScript? = s.codePoints().toArray().filter { Character.isLetter(it) }
        .groupingBy { Character.UnicodeScript.of(it) }.eachCount().maxByOrNull { it.value }?.key

    /** Hits per ready dictionary, in [order], skipping [disabled]; unready ones are queued and listed in [notReady]. */
    fun lookup(query: String, order: List<String>, disabled: Set<String>, notReady: MutableList<StarDict>? = null):
        List<Pair<StarDict, List<Hit>>> {
        val q = cleanQuery(query)
        val dicts = all(order).filter { it.id !in disabled }.filter { d ->
            d.ready.also { if (!it) { prepareAsync(d); if (status[d.id]?.startsWith("error") != true) notReady?.add(d) } }
        }
        val hits = dicts.associateWith { d -> runCatching { d.lookup(q) }.getOrDefault(emptyList()) }
        // A dictionary that knows the form (Häuser) names the base form (Haus): look that up in the others too.
        val bases = hits.values.flatten().map { it.word }.filter { !it.equals(q, true) }.distinct().take(3)
        return dicts.mapNotNull { d ->
            // The base form named by another dictionary, as written: "go" (from "went") is not German "Go".
            val own = hits.getValue(d).ifEmpty { bases.flatMap { b -> runCatching { d.lookup(b) }.getOrDefault(emptyList()).filter { it.word == b } } }
            own.takeIf { it.isNotEmpty() }?.let { d to it }
        }
    }
}

/** One line of an offline word-by-word gloss. */
data class Gloss(val word: String, val base: String, val meaning: String)

/** WikDict marks the part of speech and gender ("noun, neutral") and puts each translation in a plain <div>. */
fun grammarOf(html: String, pos: String? = null): String = GRAMMAR.findAll(html).map { it.groupValues[1].trim() }
    .let { all -> all.firstOrNull { pos != null && it.contains(pos, true) } ?: all.firstOrNull() }.orEmpty()

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
