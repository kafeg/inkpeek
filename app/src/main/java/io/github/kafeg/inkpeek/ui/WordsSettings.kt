package io.github.kafeg.inkpeek.ui

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Notes: saved words, saved phrases, the history; 50 per page, the pager and "Clear history" under the list. */
class WordsSettings(private val a: MainActivity) {
    private lateinit var tabs: LinearLayout
    private lateinit var list: LinearLayout
    private var kind = a.open["words"] ?: "word"
    private var page = 0

    fun build(t: LinearLayout) {
        tabs = Ink.vertical(a); t.addView(tabs)
        list = Ink.vertical(a); t.addView(list)
    }

    /** Tap opens the card (offline from saved answers), ✕ removes (it asks first). */
    fun refresh(): Job = a.launch {
        val lang = a.prefs.answerLang; val now = kind
        val (items, counts) = withContext(Dispatchers.IO) { runCatching {
            val st = Store.get(a)
            (if (now == "history") st.history(lang) else st.saved(now, lang)) to
                listOf(st.count("saved", "word"), st.count("saved", "phrase"), st.count("history"))
        }.getOrNull() } ?: return@launch
        tabs.removeAllViews()
        tabs.addView(Ink.subTabs(a, listOf("word", "phrase", "history").zip(listOf(R.string.list_words, R.string.list_phrases, R.string.list_history))
            .mapIndexed { i, (k, label) -> k to a.getString(label) + " (" + counts[i] + ")" }, now, 16f) { kind = it; a.open["words"] = it; page = 0; refresh() },
            Ink.lp().apply { bottomMargin = Ink.dp(a, 6) })
        list.removeAllViews()
        if (items.isEmpty()) list.addView(Rows.note(a, a.getString(if (now == "history") R.string.list_empty_history else R.string.list_empty_saved)))
        val pages = (items.size + PAGE - 1) / PAGE
        page = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
        if (pages > 1) list.addView(WordsList.pager(a, page, items.size, 15f) { page = it; turn() }, Ink.lp().apply { bottomMargin = Ink.dp(a, 6) })
        items.drop(page * PAGE).take(PAGE).forEach { e ->
            list.addView(WordsList.row(a, e, 16f, { a.startActivity(WordsList.card(a, e.text)) }) {
                Rows.confirm(a, a.getString(if (now == "history") R.string.forget_q else R.string.unsave_q, e.text)) {
                    a.launch { withContext(Dispatchers.IO) { runCatching { Store.get(a).run { if (now == "history") forget(e.text) else unsave(e.text) } } }; refresh() }
                }
            })
        }
        // Under the list: TSV and, for the history, clearing it all.
        val bar = Ink.horizontal(a).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        bar.addView(android.view.View(a), Ink.lp(0, 1, 1f))
        if (items.isNotEmpty()) bar.addView(Ink.button(a, "TSV", 15f) { export(now, items) },
            Ink.lp(wrap).apply { rightMargin = Ink.dp(a, 8) })
        if (now == "history" && items.isNotEmpty()) bar.addView(Ink.button(a, a.getString(R.string.clear_history), 15f) {
            Rows.confirm(a, a.getString(R.string.clear_history_q), R.string.clear_history) {
                a.launch { withContext(Dispatchers.IO) { runCatching { Store.get(a).clearHistory() } }; refresh() }
            }
        }, Ink.lp(wrap))
        if (bar.childCount > 1) list.addView(bar, Ink.lp().apply { topMargin = Ink.dp(a, 14) })
        // At the very bottom, for all three lists: the whole store (history, saved, AI answers) to a file and back.
        list.addView(Rows.note(a, a.getString(R.string.backup_d)), Ink.lp().apply { topMargin = Ink.dp(a, 18) })
        list.addView(Rows.buttons(a, a.getString(R.string.export) to { backup() }, a.getString(R.string.import_) to {
            a.startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*"), RESTORE) }))
    }

    /** The whole store as one file in Download/InkPeek. */
    private fun backup() = a.launch {
        val name = "inkpeek-backup-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.db"
        withContext(Dispatchers.IO) { runCatching {
            val tmp = java.io.File(a.cacheDir, "backup.db").also { Store.get(a).backup(it) }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/InkPeek")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val r = a.contentResolver
            // Pending until it is whole: a failed copy leaves no half file in Download.
            val u = r.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            try {
                r.openOutputStream(u)!!.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                r.update(u, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { r.delete(u, null, null); throw e } finally { tmp.delete() }
            // The name MediaStore gave it (a second backup the same day gets a number).
            r.query(u, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
        } }.onSuccess { a.toast(a.getString(R.string.backup_done, "Download/InkPeek/$it")) }
            .onFailure { a.toast(a.getString(R.string.failed, it.message)) }
    }

    /** A backup picked in the system's file picker: what it has and this device does not is added. */
    fun onResult(data: android.content.Intent) = a.launch {
        val uri = data.data ?: return@launch
        val added = withContext(Dispatchers.IO) { runCatching {
            val tmp = java.io.File(a.cacheDir, "restore.db")
            a.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            try { Store.get(a).restore(tmp) } finally { tmp.delete() }
        } }.getOrElse { a.toast(a.getString(R.string.restore_bad)); return@launch }
        a.toast(a.getString(R.string.restore_done, added)); refresh()
    }

    /** The open list as TSV in Download/InkPeek (text, meaning, AI translation, language, date: Anki takes it as is), then Share. */
    private fun export(kind: String, items: List<io.github.kafeg.inkpeek.Store.Entry>) = a.launch {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fun cell(s: String) = s.replace(Regex("[\t\r\n]+"), " ").trim()
        val tsv = items.joinToString("\n", postfix = "\n") { e ->
            listOf(e.text, e.meaning, e.ai, a.prefs.answerCode, day.format(java.util.Date(e.time))).joinToString("\t") { cell(it) } }
        val name = "inkpeek-$kind-${day.format(java.util.Date())}.tsv"
        val uri = withContext(Dispatchers.IO) { runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/tab-separated-values")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/InkPeek")
            }
            val r = a.contentResolver
            r.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!.also { u -> r.openOutputStream(u)!!.use { it.write(tsv.toByteArray()) } }
        } }.getOrElse { a.toast(a.getString(R.string.failed, it.message)); return@launch }
        a.toast(a.getString(R.string.exported, "Download/InkPeek/$name", items.size))
        runCatching { a.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/tab-separated-values").putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), null)) }
    }

    /** Another page starts at its top. */
    private fun turn() = a.launch { refresh().join(); a.scrollTo(tabs) }

    private val PAGE = WordsList.PAGE

    companion object { const val RESTORE = 3 }
}
