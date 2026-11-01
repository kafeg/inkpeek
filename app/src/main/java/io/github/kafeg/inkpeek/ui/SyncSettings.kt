package io.github.kafeg.inkpeek.ui

import android.content.Intent
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.Store
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

    private fun refreshSync() = a.launch {
        val uri = prefs.syncUri
        // Another app's folder is asked for its name: a slow provider must not hold the screen.
        val shown = if (uri.isEmpty()) a.getString(R.string.sync_folder_none) else withContext(Dispatchers.IO) { place(android.net.Uri.parse(uri)) }
        sync.removeAllViews()
        sync.addView(Rows.info(a, null, a.getString(R.string.sync_folder_t), shown))
        sync.addView(Rows.buttons(a, a.getString(if (uri.isEmpty()) R.string.sync_choose else R.string.sync_change) to {
            a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION), FOLDER) }))
        if (uri.isNotEmpty()) {
            val read = Sync.read()
            val state = Sync.error.takeIf { it.isNotEmpty() }?.let { a.getString(R.string.failed, it) }
                ?: if (read == 0L) a.getString(R.string.sync_not_read)
                else a.getString(R.string.sync_read, android.text.format.DateUtils.formatDateTime(a, read, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME))
            sync.addView(Rows.info(a, null, a.getString(R.string.sync_state_t), state))
            prefs.syncDevice.takeIf { it.isNotEmpty() }?.let { sync.addView(Rows.info(a, null, a.getString(R.string.sync_device_t), "${prefs.syncName} · $it")) }
        }
        sync.addView(Rows.info(a, null, a.getString(R.string.hy_what_t), a.getString(R.string.sync_what), lines = 4))
        sync.addView(Rows.info(a, null, a.getString(R.string.sync_hint_t), a.getString(R.string.sync_hint), lines = 6))
        // The whole store (history, saved, AI answers) to a file and back.
        sync.addView(Rows.note(a, a.getString(R.string.backup_d)))
        sync.addView(Rows.buttons(a, a.getString(R.string.export) to { backup() }, a.getString(R.string.import_) to {
            a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), RESTORE) }))
    }

    /** The folder as people see it: "Internal shared storage/Sync"; another app's: "Nextcloud: InkPeek". */
    private fun place(u: android.net.Uri): String = runCatching {
        val id = android.provider.DocumentsContract.getTreeDocumentId(u)
        if (u.authority == "com.android.externalstorage.documents") {
            val vol = id.substringBefore(':'); val path = id.substringAfter(':', "")
            val name = a.getSystemService(android.os.storage.StorageManager::class.java).storageVolumes
                .firstOrNull { if (vol == "primary") it.isPrimary else it.uuid.equals(vol, true) }?.getDescription(a) ?: vol
            listOf(name, path).filter { it.isNotEmpty() }.joinToString("/")
        } else {
            val app = a.packageManager.resolveContentProvider(u.authority!!, 0)?.loadLabel(a.packageManager)?.toString()
            val dn = a.contentResolver.query(android.provider.DocumentsContract.buildDocumentUriUsingTree(u, id),
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            listOfNotNull(app, dn).joinToString(": ").ifEmpty { u.toString() }
        }
    }.getOrDefault(u.toString())

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
    fun onRestore(data: android.content.Intent) = a.launch {
        val uri = data.data ?: return@launch
        val added = withContext(Dispatchers.IO) { runCatching {
            val tmp = java.io.File(a.cacheDir, "restore.db")
            a.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            try { Store.get(a).restore(tmp) } finally { tmp.delete() }
        } }.getOrElse { a.toast(a.getString(R.string.restore_bad)); return@launch }
        a.toast(a.getString(R.string.restore_done, added)); a.refreshWords()
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

    companion object { const val RESTORE = 3; const val FOLDER = 4; const val PERMISSION = 5 }
}
