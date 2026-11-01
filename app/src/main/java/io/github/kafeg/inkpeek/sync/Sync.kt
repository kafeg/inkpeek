package io.github.kafeg.inkpeek.sync

import android.content.Context
import android.net.Uri
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.Store
import io.github.kafeg.inkpeek.anki.Anki
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The sync folder in the app: every change of the store goes to this install's log at once; the folder is read when a
 * card closes and when the settings show (at most every half minute), and the store becomes the merged log. A look is
 * written when its card closes, with the line it got. One thread for all of it, the Anki sending too.
 */
object Sync {
    private lateinit var app: Context
    private val exec = Executors.newSingleThreadExecutor()
    private var writer: Writer? = null
    private var writerOf = ""
    private val looks = LinkedHashSet<String>()
    /** Lines not written yet, each with its record's own time (null: the clock's). */
    private val failed = ArrayList<Pair<Line, Long?>>()
    private var applied = 0
    private var readAt = 0L
    /** What went wrong last ("" = nothing): the Sync tab shows it instead of the read time. */
    @Volatile var error = ""
    /** Called on the main thread after a read or a send (the open settings refresh). */
    @Volatile var changed: (() -> Unit)? = null

    fun init(ctx: Context) { app = ctx.applicationContext }
    private val prefs get() = Prefs(app)
    private val on get() = ::app.isInitialized && prefs.syncUri.isNotEmpty()

    /** A look counted in the store: its line is written by [refresh] (the card adds its line and base form first). */
    fun look(text: String) { if (on) synchronized(looks) { looks += text } }

    fun record(lines: List<Line>) { if (on && lines.isNotEmpty()) exec.execute { write(lines) } }

    /** Reads the folder (not again within half a minute unless [force]), then sends to Anki. */
    fun refresh(force: Boolean = false) {
        if (!::app.isInitialized) return
        exec.execute {
            if (on) runCatching { pull(force) }.onFailure { fail(it) }
            runCatching { Anki.auto(app) }
            changed?.let { f -> android.os.Handler(android.os.Looper.getMainLooper()).post { f() } }
        }
    }

    /** A folder picked: what this store has and the log lacks goes there first (the time of each record kept), then it is read. */
    fun choose(uri: String) {
        prefs.syncUri = uri; prefs.syncRead = 0
        exec.execute { error = ""; writer = null; applied = 0 }
        export(); refresh(force = true)
    }

    fun export() { if (on) exec.execute { runCatching { exportNow() }.onFailure { fail(it) } } }

    private fun fail(e: Throwable) { error = e.message ?: e.javaClass.simpleName; writer = null }

    private fun now() = System.currentTimeMillis()

    /** The folder's log as this install sees it; a new id when another install writes with this one (formats.md). */
    private fun writer(): Writer {
        val p = prefs; val uri = p.syncUri
        writer?.takeIf { writerOf == uri }?.let { return it }
        val folder = SafFolder(app.contentResolver, Uri.parse(uri))
        var id = p.syncDevice
        if (id.isEmpty() || !SyncLog.keep(SyncLog.read(folder, id).filter { it.d == id }, p.syncLast.ifEmpty { null })) {
            val abc = "abcdefghijklmnopqrstuvwxyz234567"; val rnd = java.security.SecureRandom()
            id = android.os.Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }.take(12).ifEmpty { "android" } +
                "-" + (1..8).map { abc[rnd.nextInt(abc.length)] }.joinToString("")
            p.syncDevice = id; p.syncCreated = now(); p.syncLast = ""
        }
        return Writer(folder, id, "inkpeek", p.syncName, p.syncCreated).also { it.read(); writer = it; writerOf = uri }
    }

    private fun write(lines: List<Line>, at: (Line) -> Long? = { null }) {
        val todo = failed + lines.map { it to at(it) }; failed.clear()
        try {
            val w = writer()
            // An answer the log has already (saved again, a copy) is not written twice.
            val fresh = todo.filter { (l, _) -> l.op != "ai.put" || w.lines.none { it.op == "ai.put" && it.text == l.text && it.lang == l.lang &&
                it.section == l.section && it.ver == l.ver && it.model == l.model && it.answer == l.answer } }.map { (l, t) -> coded(l) to t }
            val times = java.util.IdentityHashMap<Line, Long>().apply { fresh.forEach { (l, t) -> if (t != null) put(l, t) } }
            w.write(fresh.map { it.first }, now()) { times[it] }
            prefs.syncLast = w.last?.raw.orEmpty(); error = ""
        } catch (e: Exception) { failed += todo; prefs.syncPending = true; fail(e) }
    }

    private fun flush() {
        val texts = synchronized(looks) { looks.toList().also { looks.clear() } }
        if (texts.isNotEmpty()) Store.get(app).let { st -> write(texts.mapNotNull { st.look(it) }) }
    }

    private fun pull(force: Boolean) {
        flush()
        if (!force && now() - readAt < 30_000) return
        val w = writer().apply { read() }
        readAt = now(); prefs.syncRead = readAt
        // A write failed (in this run or one that ended since): what the store has and the log lacks goes first.
        if (failed.isNotEmpty() || prefs.syncPending) exportNow()
        if (failed.isNotEmpty()) return // the store has what the log lacks: it waits until the lines are written
        val s = SyncLog.merge(w.lines)
        if (s.hashCode() == applied) return
        Store.get(app).replace(State(s.saved.map { it.copy(lang = name(it.lang)) }, s.history.map { it.copy(lang = name(it.lang)) }, s.ai),
            synchronized(looks) { looks.toSet() })
        applied = s.hashCode(); error = ""
    }

    private fun exportNow() {
        flush()
        // The failed lines first: the drafts below then count only what they lack.
        if (failed.isNotEmpty()) write(emptyList())
        if (failed.isNotEmpty()) return
        val w = writer(); val m = SyncLog.merge(w.lines); val local = Store.get(app).records()
        val saved = m.saved.map { it.text }.toSet(); val counts = m.history.associate { it.text to it.count }
        val ai = m.ai.map { listOf(it.text, it.lang, it.section, it.ver) }.toSet()
        val drafts = local.saved.filter { it.text !in saved }.map { Line(it.created, 0, "", "saved.put", it.text, it.kind, it.lang, it.meaning) } +
            local.history.filter { it.count > (counts[it.text] ?: 0) }.map {
                Line(it.seen, 0, "", "history.seen", it.text, it.kind, it.lang, it.meaning, it.base.orEmpty(), it.count - (counts[it.text] ?: 0) - 1) } +
            local.ai.filter { listOf(it.text, it.lang, it.section, it.ver) !in ai }.map {
                Line(it.created, 0, "", "ai.put", it.text, lang = it.lang, section = it.section, ver = it.ver, model = it.model, answer = it.answer) }
        write(drafts.sortedBy { it.ms }) { it.ms }
        if (failed.isEmpty()) prefs.syncPending = false
    }

    /** The store keeps a line's language by its English name ("Russian"); the log, as LexiPeek, by its code ("ru"). AI answers keep theirs. */
    private val codes by lazy { Locale.getISOLanguages().associateBy { Locale(it).getDisplayLanguage(Locale.ENGLISH) } }
    fun code(lang: String) = codes[lang] ?: lang
    private fun name(lang: String) = if (lang.matches(Regex("[a-z]{2,3}"))) Locale(lang).getDisplayLanguage(Locale.ENGLISH) else lang
    private fun coded(l: Line) = if (l.op == "saved.put" || l.op == "history.seen") l.copy(lang = code(l.lang)) else l

    /** The Sync tab's line: the error, else when the folder was read (ms; 0 = not yet). */
    fun read() = prefs.syncRead
}
