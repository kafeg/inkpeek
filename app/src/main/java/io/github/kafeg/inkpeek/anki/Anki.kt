package io.github.kafeg.inkpeek.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.Store
import io.github.kafeg.inkpeek.stardict.normKey
import io.github.kafeg.inkpeek.sync.Sync
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Anki through AnkiDroid's content provider (its public API): saved words, phrases and, when on, the history go to
 * their decks by themselves, one note per entry by its Key — the same note type, fields and Key as LexiPeek's, so one
 * word from both apps is one note after AnkiWeb sync. No audio: an Audio a note has is kept.
 */
object Anki {
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    const val MODEL = "Peek"
    val FIELDS = listOf("Front", "Back", "Audio", "Example", "Key")
    val KINDS = listOf("word", "phrase", "history")
    private const val AUTHORITY = "com.ichi2.anki.flashcards"
    private const val SEP = "\u001f"

    private const val FRONT = """<div class="front">{{Front}}</div>{{Audio}}"""
    private const val BACK = """{{FrontSide}}<hr id=answer><div class="back">{{Back}}</div>{{#Example}}<div class="example">{{Example}}</div>{{/Example}}"""
    private const val CSS = ".card { font-family: arial, sans-serif; font-size: 20px; text-align: center; color: black; background: white; }\n" +
        ".front { font-size: 26px; }\n.back { font-size: 18px; }\n.head { font-size: 16px; color: #555; margin-bottom: 4px; }\n" +
        ".example { font-size: 15px; color: #666; font-style: italic; margin-top: 10px; }\n" +
        ".nightMode .head, .nightMode .example { color: #aaa; }\n"

    /** The answer language, then 32 hex digits of sha256 of the text's lookup key; "h-" before a history note's. */
    fun key(text: String, lang: String, history: Boolean = false) =
        (if (history) "h-" else "") + lang.lowercase(Locale.ROOT) + "-" + sha(normKey(text)).take(32)

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun html(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")

    /** "das Haus (Hauses, pl. Häuser) — дом": the header apart from the meaning, as LexiPeek's. */
    private fun back(line: String) = line.indexOf(" — ").let { i -> if (i < 0) html(line) else "<div class=\"head\">${html(line.take(i))}</div>${html(line.substring(i + 3))}" }

    data class Note(val key: String, val kind: String, val front: String, val back: String) {
        val digest get() = sha(front + SEP + back + SEP).take(16)
    }

    /** The entries of [kinds] as notes. */
    fun notes(ctx: Context, kinds: Collection<String>): List<Note> {
        val st = Store.get(ctx); val ans = Prefs(ctx).answerCode
        return kinds.flatMap { k -> st.anki(k == "history").filter { k == "history" || it.kind == k }.map { e ->
            Note(key(e.text, Sync.code(e.lang).ifEmpty { ans }, k == "history"), k, html(e.text), back(e.meaning)) } }
    }

    private var sentCache: MutableMap<String, String>? = null
    /** Key → the digest that went; per device (AnkiDroid's collection is), in the app's own files. */
    private fun sent(ctx: Context): MutableMap<String, String> = sentCache ?: HashMap<String, String>().also { m ->
        runCatching { JSONObject(File(ctx.filesDir, "anki.json").readText()).getJSONObject("sent").let { j -> j.keys().forEach { m[it] = j.getString(it) } } }
        sentCache = m
    }
    private fun save(ctx: Context, m: Map<String, String>) {
        val tmp = File(ctx.filesDir, "anki.json.tmp"); tmp.writeText(JSONObject().put("sent", JSONObject(m)).toString())
        tmp.renameTo(File(ctx.filesDir, "anki.json"))
    }

    @Synchronized fun inAnki(ctx: Context, text: String, lang: String, history: Boolean) =
        sent(ctx).containsKey(key(text, Sync.code(lang).ifEmpty { Prefs(ctx).answerCode }, history))

    /** Per list: (in Anki, waiting). */
    @Synchronized fun counts(ctx: Context): Map<String, Pair<Int, Int>> = sent(ctx).let { s ->
        notes(ctx, KINDS).groupBy { it.kind }.let { g -> KINDS.associateWith { k -> g[k].orEmpty().let { ns -> ns.count { it.key in s } to ns.count { s[it.key] != it.digest } } } }
    }

    enum class State { NOT_INSTALLED, NO_PERMISSION, OK, FAILED }
    data class Check(val state: State, val decks: List<String> = emptyList(), val error: String = "")

    /** Which step fails: AnkiDroid, its permission, its answer; reads only. */
    fun check(ctx: Context): Check = when {
        ctx.packageManager.resolveContentProvider(AUTHORITY, 0) == null -> Check(State.NOT_INSTALLED)
        ctx.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED -> Check(State.NO_PERMISSION)
        else -> runCatching { Check(State.OK, decks(ctx.contentResolver).keys.sorted()) }.getOrElse { Check(State.FAILED, error = it.message.orEmpty()) }
    }

    /** The last send's outcome for the tab ("" = fine). */
    @Volatile var last = ""
    private var retry = 0L

    /** What waits goes, when sending is on and AnkiDroid answers; a failure is tried again a minute later, or by [force]. */
    @Synchronized fun auto(ctx: Context, force: Boolean = false) {
        val p = Prefs(ctx)
        if (!p.ankiOn || check(ctx).state != State.OK || (!force && System.currentTimeMillis() < retry)) return
        val s = sent(ctx)
        val wait = notes(ctx, KINDS.filter { p.ankiDeckOn(it) }).filter { s[it.key] != it.digest }
        if (wait.isEmpty()) { last = ""; return }
        last = runCatching { send(ctx.contentResolver, wait, p, s); "" }.getOrElse { e ->
            retry = System.currentTimeMillis() + 60_000
            if (e is Missing) ctx.getString(R.string.anki_model_changed, MODEL, e.field) else ctx.getString(R.string.anki_refused, e.message ?: e.javaClass.simpleName)
        }
        save(ctx, s)
    }

    private class Missing(val field: String) : Exception(field)

    private fun uri(path: String) = Uri.parse("content://$AUTHORITY/$path")

    private fun decks(r: ContentResolver): Map<String, Long> = r.query(uri("decks"), arrayOf("deck_id", "deck_name"), null, null, null)!!.use { c ->
        generateSequence { if (c.moveToNext()) c.getString(1) to c.getLong(0) else null }.toMap() }

    /** The note type "Peek": found (its field order), else made; one without our fields is not touched. */
    private fun model(r: ContentResolver): Pair<Long, List<String>> {
        r.query(uri("models"), null, null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("name")) == MODEL) {
                val fs = c.getString(c.getColumnIndexOrThrow("field_names")).split(SEP)
                FIELDS.firstOrNull { it !in fs }?.let { throw Missing(it) }
                return c.getLong(c.getColumnIndexOrThrow("_id")) to fs
            }
        }
        val u = r.insert(uri("models"), ContentValues().apply {
            put("name", MODEL); put("field_names", FIELDS.joinToString(SEP)); put("num_cards", 1); put("css", CSS); put("sort_field_index", 0) })!!
        r.update(Uri.withAppendedPath(u, "templates/0"), ContentValues().apply {
            put("card_template_name", "Card 1"); put("question_format", FRONT); put("answer_format", BACK) }, null, null)
        return u.lastPathSegment!!.toLong() to FIELDS
    }

    private fun send(r: ContentResolver, wait: List<Note>, p: Prefs, sent: MutableMap<String, String>) {
        val (mid, fields) = model(r)
        val decks = decks(r).toMutableMap()
        for ((kind, notes) in wait.groupBy { it.kind }) {
            val name = p.ankiDeck(kind)
            val did = decks[name] ?: r.insert(uri("decks"), ContentValues().apply { put("deck_name", name) })!!.lastPathSegment!!.toLong().also { decks[name] = it }
            for (part in notes.chunked(50)) {
                // Each Key's notes: more than one when two devices added it before AnkiWeb synced.
                val found = HashMap<String, MutableList<Pair<Long, List<String>>>>()
                r.query(uri("notes"), arrayOf("_id", "flds"), "\"note:$MODEL\" (" + part.joinToString(" OR ") { "Key:${it.key}" } + ")", null, null)?.use { c ->
                    while (c.moveToNext()) c.getString(1).split(SEP).let { f -> found.getOrPut(f.getOrElse(fields.indexOf("Key")) { "" }) { ArrayList() } += c.getLong(0) to f } }
                fun flds(n: Note, old: List<String>?) = fields.mapIndexed { i, f -> when (f) {
                    "Front" -> n.front; "Back" -> n.back; "Example" -> ""; "Key" -> n.key; else -> old?.getOrNull(i).orEmpty() } }.joinToString(SEP)
                val add = part.filter { it.key !in found }
                part.filter { it.key in found }.forEach { n -> found.getValue(n.key).forEach { (id, old) ->
                    r.update(uri("notes/$id"), ContentValues().apply { put("flds", flds(n, old)) }, null, null) } }
                if (add.isNotEmpty()) {
                    val n = r.bulkInsert(uri("notes").buildUpon().appendQueryParameter("deckId", did.toString()).build(),
                        add.map { ContentValues().apply { put("mid", mid); put("flds", flds(it, null)); put("tags", "inkpeek") } }.toTypedArray())
                    if (n != add.size) throw IllegalStateException("$n / ${add.size}")
                }
                part.forEach { sent[it.key] = it.digest }
            }
        }
    }
}
