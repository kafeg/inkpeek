package io.github.kafeg.inkpeek.ui

import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.anki.Anki
import io.github.kafeg.inkpeek.sync.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sync: the folder, how to keep it the same everywhere, when it was read · Anki: sending to AnkiDroid, its decks. */
class SyncSettings(private val a: MainActivity) {
    private val prefs get() = a.prefs
    private lateinit var sync: LinearLayout
    private lateinit var anki: LinearLayout

    fun build(t: LinearLayout) {
        sync = Ink.vertical(a); anki = Ink.vertical(a)
        a.tabPick["sync"] = Rows.subTabs(a, t, listOf(Triple("sync", a.getString(R.string.sync_t), sync), Triple("anki", a.getString(R.string.anki_tab), anki)),
            a.open["sync"] ?: "sync") { a.open["sync"] = it; refresh() }
    }

    fun refresh() { refreshSync(); refreshAnki() }

    private fun refreshSync() {
        sync.removeAllViews()
        val uri = prefs.syncUri
        // "primary:Sync/InkPeek" → "Sync/InkPeek".
        val shown = if (uri.isEmpty()) a.getString(R.string.sync_folder_none) else runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri)).substringAfter(':').ifEmpty { "/" } }.getOrDefault(uri)
        sync.addView(Rows.info(a, null, a.getString(R.string.sync_folder_t), shown))
        sync.addView(Rows.buttons(a, a.getString(if (uri.isEmpty()) R.string.sync_choose else R.string.sync_change) to {
            a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION), FOLDER) }))
        sync.addView(Rows.info(a, null, a.getString(R.string.sync_hint_t), a.getString(R.string.sync_hint), lines = 6))
        if (uri.isEmpty()) return
        val read = Sync.read()
        val state = Sync.error.takeIf { it.isNotEmpty() }?.let { a.getString(R.string.failed, it) }
            ?: if (read == 0L) a.getString(R.string.sync_not_read)
            else a.getString(R.string.sync_read, android.text.format.DateUtils.formatDateTime(a, read, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME))
        sync.addView(Rows.info(a, null, a.getString(R.string.sync_state_t), state))
    }

    /** A folder picked in the system's picker: kept across restarts, then synced. */
    fun onFolder(data: Intent) {
        val u = data.data ?: return
        runCatching { a.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        Sync.choose(u.toString()); refresh()
    }

    private fun refreshAnki(): kotlinx.coroutines.Job = a.launch {
        val (check, counts) = withContext(Dispatchers.IO) { Anki.check(a) to runCatching { Anki.counts(a) }.getOrDefault(emptyMap()) }
        anki.removeAllViews()
        anki.addView(Rows.info(a, null, a.getString(R.string.anki_how_t), a.getString(R.string.anki_how_d), lines = 4))
        anki.addView(Rows.check(a, a.getString(R.string.anki_on_t), a.getString(R.string.anki_on_d), prefs.ankiOn) {
            prefs.ankiOn = it; if (it && check.state == Anki.State.NO_PERMISSION) ask() else if (it) Sync.refresh() })
        anki.addView(Rows.note(a, a.getString(R.string.anki_lexipeek)))
        // The step that fails, else the connection; a tap asks for the permission.
        val (title, about) = when (check.state) {
            Anki.State.NOT_INSTALLED -> R.string.anki_none_t to a.getString(R.string.anki_none_d)
            Anki.State.NO_PERMISSION -> R.string.anki_perm_t to a.getString(R.string.anki_perm_d)
            Anki.State.FAILED -> R.string.anki_fail_t to check.error
            Anki.State.OK -> R.string.anki_ok_t to a.getString(R.string.anki_ok_d, check.decks.size)
        }
        anki.addView(Rows.info(a, null, a.getString(title), about, open = if (check.state == Anki.State.NO_PERMISSION) ({ ask() }) else null))
        val waiting = Anki.KINDS.filter { prefs.ankiDeckOn(it) }.sumOf { counts[it]?.second ?: 0 }
        val state = Anki.last.ifEmpty { if (waiting > 0) a.getString(R.string.anki_waiting, waiting) else a.getString(R.string.anki_all_sent) }
        anki.addView(Rows.info(a, null, a.getString(R.string.anki_send_t), state))
        anki.addView(Rows.buttons(a, a.getString(R.string.anki_send_btn) to {
            a.launch { withContext(Dispatchers.IO) { runCatching { Anki.auto(a, force = true) } }; refreshAnki() } }))
        anki.addView(Rows.heading(a, a.getString(R.string.anki_decks_t)))
        anki.addView(Rows.note(a, a.getString(R.string.anki_decks_d)))
        val names = mapOf("word" to (R.string.anki_words_t to R.string.anki_words_d), "phrase" to (R.string.anki_phrases_t to R.string.anki_phrases_d),
            "history" to (R.string.anki_history_t to R.string.anki_history_d))
        for (k in Anki.KINDS) {
            val (t, d) = names.getValue(k)
            val c = counts[k]
            val on = prefs.ankiDeckOn(k)
            anki.addView(Rows.check(a, a.getString(t), if (on && prefs.ankiOn && c != null) a.getString(R.string.anki_counts, c.first, c.second) else a.getString(d), on) {
                prefs.setAnkiDeckOn(k, it); refreshAnki(); Sync.refresh() })
            anki.addView(deck(k, check.decks))
        }
    }

    /** A deck AnkiDroid has (the list drops down) or a new name: it is made on the first send. */
    private fun deck(kind: String, decks: List<String>) = AutoCompleteTextView(a).apply {
        setText(prefs.ankiDeck(kind)); textSize = 17f; setSingleLine(); threshold = 1
        hint = a.getString(R.string.anki_deck_hint)
        setAdapter(ArrayAdapter(a, android.R.layout.simple_dropdown_item_1line, decks))
        setDropDownBackgroundDrawable(Ink.border(a, 3, 2))
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        fun save() { prefs.setAnkiDeck(kind, text.toString()) }
        setOnClickListener { if (decks.isNotEmpty()) showDropDown() }
        setOnItemClickListener { _, _, _, _ -> save() }
        setOnEditorActionListener { v, _, _ -> save(); v.clearFocus(); false }
        setOnFocusChangeListener { _, has -> if (!has) save() }
        layoutParams = Ink.lp().apply { leftMargin = Ink.dp(a, 40) }
    }

    private fun ask() = a.requestPermissions(arrayOf(Anki.PERMISSION), PERMISSION)

    /** AnkiDroid's permission answered; refused for good: the app's page in the system settings, where it can be given. */
    fun onPermission(granted: Boolean) {
        if (!granted && !a.shouldShowRequestPermissionRationale(Anki.PERMISSION)) runCatching {
            a.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${a.packageName}"))) }
        if (granted) Sync.refresh()
        refreshAnki()
    }

    companion object { const val FOLDER = 4; const val PERMISSION = 5 }
}
