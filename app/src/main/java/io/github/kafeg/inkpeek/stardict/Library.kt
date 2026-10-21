package io.github.kafeg.inkpeek.stardict

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The language packs' dictionaries under [packRoot] (dict-<x>-<y> folders), then the user's own under [ownRoots] (imported,
 * downloaded, folders like KOReader's); the card uses those [use] lets in (a pack: its pair is ticked; own: switched on).
 * Indexes are built by one background thread, one dictionary at a time; [status] tells the UI what is going on.
 */
class Library(private val packRoot: File, private val ownRoots: List<File>, private val workRoot: File,
              private val use: (StarDict, Boolean) -> Boolean = { _, _ -> true }, private val order: () -> List<String> = { emptyList() }) {
    private val roots = listOf(packRoot) + ownRoots
    private val packPath = packRoot.canonicalPath + File.separator
    private var cache: Map<String, StarDict> = emptyMap()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "inkpeek-index").apply { isDaemon = true } }
    private val queued = ConcurrentHashMap.newKeySet<String>()
    /** id -> "indexing" | "error: …"; absent = ready or not yet looked at. */
    val status = ConcurrentHashMap<String, String>()

    /** The dictionaries in use, in the person's order (Card → Main); the others after them, the packs first, by name. */
    fun all(): List<StarDict> = found().filter { use(it, isPack(it)) }

    /** Every dictionary on disk, in use or not. */
    @Synchronized fun found(): List<StarDict> {
        val found = roots.flatMap { StarDict.scan(it) }.map { it.canonicalFile }.distinctBy { it.path }
        val byPath = cache.values.associateBy { it.ifo.path }
        cache = found.map { byPath[it.path] ?: StarDict(it, workRoot) }.associateBy { it.id }
        // Drop the index of a dictionary that is gone from disk (not merely unreadable right now).
        workRoot.listFiles()?.filter { it.name !in cache }?.forEach { dir ->
            val src = File(dir, "source").takeIf { it.exists() }?.readText()
            if (src == null || (roots.any { it.isDirectory } && !File(src).exists())) dir.deleteRecursively()
        }
        val rank = order().withIndex().associate { it.value to it.index }
        return cache.values.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { !isPack(it) }, { it.name }))
    }

    fun isPack(d: StarDict) = d.ifo.path.startsWith(packPath)

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
     * Hits per ready dictionary: the packs into [answer] (the app's language) and the own ones; the other packs only when
     * none of those knows the word (an Italian book with no Italian → Russian pack). Unready ones are queued and listed
     * in [notReady].
     */
    fun lookup(query: String, answer: String, notReady: MutableList<StarDict>? = null, among: List<StarDict> = all()): List<Pair<StarDict, List<Hit>>> {
        val q = cleanQuery(query)
        val dicts = among.filter { d ->
            d.ready.also { if (!it) { prepareAsync(d); if (status[d.id]?.startsWith("error") != true) notReady?.add(d) } }
        }
        val hits = dicts.associateWith { d -> runCatching { d.lookup(q) }.getOrDefault(emptyList()) }
        // A dictionary that knows the form (Häuser) names the base form (Haus): look that up in the others too.
        val bases = hits.values.flatten().map { it.word }.filter { !it.equals(q, true) }.distinct().take(3)
        return dicts.mapNotNull { d ->
            // The base form named by another dictionary, as written: "go" (from "went") is not German "Go".
            val own = hits.getValue(d).ifEmpty { bases.flatMap { b -> runCatching { d.lookup(b) }.getOrDefault(emptyList()).filter { it.word == b } } }
            own.takeIf { it.isNotEmpty() }?.let { d to it }
        }.let { all -> all.filter { (d, _) -> !isPack(d) || d.answer == answer }.ifEmpty { all } }
    }

    /**
     * The word's header from the packs, one per book language (de→ru and de→en describe the same German word), the
     * answer language's first: "das Haus (Hauses, мн. Häuser)". [among]: the packs to ask, in use or not.
     */
    fun heads(query: String, answer: String, among: List<StarDict> = all()): List<Head> =
        lookup(query, answer, among = among.filter(::isPack)).distinctBy { (d, _) -> d.book }
        .mapNotNull { (_, hits) -> hits.firstNotNullOfOrNull { h -> headLine(h.html)?.let { Head(h.word, it) } } }

    /**
     * Word-by-word help for a phrase or passage: each distinct word with its base form's first line. Only
     * dictionaries that know most of the text count (so an English dictionary does not explain German "aus" as "AU");
     * among them a pack into [answer] wins, then the one that knows more words of this text; an own dictionary gives its
     * first meaning.
     */
    fun gloss(text: String, answer: String, limit: Int = 60): List<Gloss> {
        val all = Regex("[\\p{L}][\\p{L}'’-]*").findAll(text).toList()
        val words = all.map { it.value }.distinctBy { it.lowercase() }.take(limit)
        // A sentence's first word is capitalized by spelling, not by meaning: "Er" is "er", "Der" is "der".
        val starts = all.filter { m -> text.substring(0, m.range.first).trimEnd().let { it.isEmpty() || it.last() in ".!?…:" } }.map { it.value }.toSet()
        val found = words.associateWith { w ->
            (if (w in starts && w != w.lowercase()) lookup(w.lowercase(), answer).filter { (_, h) -> h.first().word == w.lowercase() } else emptyList())
                .ifEmpty { lookup(w, answer) }
        }
        val known = found.values.flatMap { r -> r.map { it.first.id } }.groupingBy { it }.eachCount()
        val top = known.values.maxOrNull() ?: 0
        return words.map { w ->
            found.getValue(w).filter { (d, _) -> (known[d.id] ?: 0) * 2 >= top }
                .mapNotNull { (d, hits) -> head(hits.first())?.let { d to it } }
                .filter { it.second.meaning.isNotBlank() }
                .sortedWith(compareBy({ it.first.answer != answer }, { -(known[it.first.id] ?: 0) }))
                .firstOrNull()?.let { (_, h) -> Gloss(w, h) } ?: Gloss(w, null)
        }
    }
}

/** A pack dictionary's languages from its folder dict-<x>-<y>: the book's and the answers'; null for other dictionaries. */
val StarDict.book: String? get() = PACK_DIR.matchEntire(ifo.parentFile?.name.orEmpty())?.groupValues?.get(1)
val StarDict.answer: String? get() = PACK_DIR.matchEntire(ifo.parentFile?.name.orEmpty())?.groupValues?.get(2)
private val PACK_DIR = Regex("dict-([a-z]{2,3})-([a-z]{2,3})")

/** An article's first line: a pack's own, else "word — its first meaning" (other dictionaries). */
fun head(h: Hit): Head? = headLine(h.html)?.let { Head(h.word, it) }
    ?: runCatching { shortMeaning(h.html, h.word) }.getOrDefault("").takeIf { it.isNotBlank() }?.let { Head(h.word, "${h.word} — $it") }

/** One line of an offline word-by-word gloss: the [word] of the text and its base form's first line, if any. */
data class Gloss(val word: String, val head: Head?) {
    val base: String get() = head?.word ?: word
    val meaning: String get() = head?.meaning.orEmpty()
}

/** A pack article's first line: [word] is the headword, [line] "das Haus (Hauses, мн. Häuser) — дом, здание". */
data class Head(val word: String, val line: String) {
    val header: String get() = line.substringBefore(" — ").trim()
    val meaning: String get() = line.substringAfter(" — ", "").trim()
}

/** A pack article's first line as text: "das Haus (Hauses, мн. Häuser) — дом, здание". */
fun headLine(html: String): String? = Regex("""<p class="head">(.*?)</p>""").find(html)?.groupValues?.get(1)
    ?.replace(Regex("<[^>]+>"), "")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&quot;", "\"")?.replace("&#x27;", "'")
    ?.replace("&amp;", "&")?.trim()
