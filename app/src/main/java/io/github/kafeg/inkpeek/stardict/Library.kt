package io.github.kafeg.inkpeek.stardict

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The language packs' dictionaries under [roots] (dict-<x>-<y> folders). Indexes are built by one background thread,
 * one dictionary at a time; [status] tells the UI what is going on.
 */
class Library(private val roots: List<File>, private val workRoot: File) {
    private var cache: Map<String, StarDict> = emptyMap()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "inkpeek-index").apply { isDaemon = true } }
    private val queued = ConcurrentHashMap.newKeySet<String>()
    /** id -> "indexing" | "error: …"; absent = ready or not yet looked at. */
    val status = ConcurrentHashMap<String, String>()

    @Synchronized fun all(): List<StarDict> {
        val found = roots.flatMap { StarDict.scan(it) }.map { it.canonicalFile }.distinctBy { it.path }
        val byPath = cache.values.associateBy { it.ifo.path }
        cache = found.map { byPath[it.path] ?: StarDict(it, workRoot) }.associateBy { it.id }
        // Drop the index of a dictionary that is gone from disk (not merely unreadable right now).
        workRoot.listFiles()?.filter { it.name !in cache }?.forEach { dir ->
            val src = File(dir, "source").takeIf { it.exists() }?.readText()
            if (src == null || (roots.any { it.isDirectory } && !File(src).exists())) dir.deleteRecursively()
        }
        return cache.values.sortedBy { it.name }
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
     * Hits per ready dictionary, the ones into [answer] (the app's language) first; unready ones are queued and
     * listed in [notReady].
     */
    fun lookup(query: String, answer: String, notReady: MutableList<StarDict>? = null): List<Pair<StarDict, List<Hit>>> {
        val q = cleanQuery(query)
        val dicts = all().filter { d ->
            d.ready.also { if (!it) { prepareAsync(d); if (status[d.id]?.startsWith("error") != true) notReady?.add(d) } }
        }
        val hits = dicts.associateWith { d -> runCatching { d.lookup(q) }.getOrDefault(emptyList()) }
        // A dictionary that knows the form (Häuser) names the base form (Haus): look that up in the others too.
        val bases = hits.values.flatten().map { it.word }.filter { !it.equals(q, true) }.distinct().take(3)
        return dicts.mapNotNull { d ->
            // The base form named by another dictionary, as written: "go" (from "went") is not German "Go".
            val own = hits.getValue(d).ifEmpty { bases.flatMap { b -> runCatching { d.lookup(b) }.getOrDefault(emptyList()).filter { it.word == b } } }
            own.takeIf { it.isNotEmpty() }?.let { d to it }
        }.sortedBy { (d, _) -> d.answer != answer }
    }

    /**
     * The word's header from the packs, one per book language (de→ru and de→en describe the same German word), the
     * answer language's first: "das Haus (Hauses, мн. Häuser)".
     */
    fun heads(query: String, answer: String): List<Head> = lookup(query, answer).distinctBy { (d, _) -> d.book }
        .mapNotNull { (_, hits) -> hits.firstNotNullOfOrNull { h -> headLine(h.html)?.let { Head(h.word, it) } } }

    /**
     * Word-by-word help for a phrase or passage: each distinct word with its base form and first meanings. Only
     * dictionaries that know most of the text count (so an English dictionary does not explain German "aus" as "AU");
     * among them the one into [answer] wins, then the one that knows more words of this text.
     */
    fun gloss(text: String, answer: String, limit: Int = 60): List<Gloss> {
        val words = Regex("[\\p{L}][\\p{L}'’-]*").findAll(text).map { it.value }.distinctBy { it.lowercase() }.take(limit).toList()
        val found = words.associateWith { lookup(it, answer) }
        val known = found.values.flatMap { r -> r.map { it.first.id } }.groupingBy { it }.eachCount()
        val top = known.values.maxOrNull() ?: 0
        return words.map { w ->
            found.getValue(w).filter { (d, _) -> (known[d.id] ?: 0) * 2 >= top }
                .mapNotNull { (d, hits) -> headLine(hits.first().html)?.let { Triple(d, hits.first().word, Head(hits.first().word, it).meaning) } }
                .filter { it.third.isNotBlank() }
                .sortedWith(compareBy({ it.first.answer != answer }, { -(known[it.first.id] ?: 0) }))
                .firstOrNull()?.let { (_, base, m) -> Gloss(w, base, m) } ?: Gloss(w, w, "")
        }
    }
}

/** A pack dictionary's languages from its folder dict-<x>-<y>: the book's and the answers'. */
val StarDict.book: String? get() = ifo.parentFile?.name?.split('-')?.getOrNull(1)
val StarDict.answer: String? get() = ifo.parentFile?.name?.split('-')?.getOrNull(2)

/** One line of an offline word-by-word gloss. */
data class Gloss(val word: String, val base: String, val meaning: String)

/** A pack article's first line: [word] is the headword, [line] "das Haus (Hauses, мн. Häuser) — дом, здание". */
data class Head(val word: String, val line: String) {
    val header: String get() = line.substringBefore(" — ").trim()
    val meaning: String get() = line.substringAfter(" — ", "").trim()
}

/** A pack article's first line as text: "das Haus (Hauses, мн. Häuser) — дом, здание". */
fun headLine(html: String): String? = Regex("""<p class="head">(.*?)</p>""").find(html)?.groupValues?.get(1)
    ?.replace(Regex("<[^>]+>"), "")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&quot;", "\"")?.replace("&#x27;", "'")
    ?.replace("&amp;", "&")?.trim()
