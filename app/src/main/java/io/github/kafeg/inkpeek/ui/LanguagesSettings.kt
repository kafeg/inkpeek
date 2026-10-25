package io.github.kafeg.inkpeek.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.packs.Component
import io.github.kafeg.inkpeek.packs.Packs
import io.github.kafeg.inkpeek.stardict.Catalog
import io.github.kafeg.inkpeek.stardict.Importer
import io.github.kafeg.inkpeek.stardict.Library
import io.github.kafeg.inkpeek.stardict.StarDict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Languages: InkPeek's language packs, translation (the answers' language, the offline method), StarDict dictionaries (one folder). */
class LanguagesSettings(private val a: MainActivity) {
    private val prefs get() = a.prefs
    private lateinit var packList: LinearLayout
    private lateinit var ownList: LinearLayout
    private lateinit var folderBox: LinearLayout
    private var manifest: List<Component> = emptyList()
    private var manifestError: String? = null

    fun build(t: LinearLayout) {
        if (!Environment.isExternalStorageManager())
            t.addView(Ink.button(a, a.getString(R.string.allow_files, prefs.home.path), Rows.BUTTON) {
                a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${a.packageName}")))
            }, Ink.lp().apply { bottomMargin = Ink.dp(a, 10) })
        val packs = Ink.vertical(a); val translation = Ink.vertical(a); val other = Ink.vertical(a)
        a.tabPick["lang"] = Rows.subTabs(a, t, listOf(Triple("packs", a.getString(R.string.lang_packs), packs), Triple("translation", a.getString(R.string.lang_translation), translation),
            Triple("other", a.getString(R.string.lang_own_tab), other)), a.open["lang"] ?: "packs") { a.open["lang"] = it }
        buildPacks(packs); buildTranslation(translation); buildOther(other)
        loadManifest()
    }

    // --- InkPeek ---
    private fun buildPacks(t: LinearLayout) {
        t.addView(Rows.heading(a, a.getString(R.string.packs_title), first = true))
        t.addView(Rows.note(a, a.getString(R.string.packs_source_d)))
        packList = Ink.vertical(a); t.addView(packList)
        t.addView(Rows.buttons(a, a.getString(R.string.packs_get) to { a.startActivity(Intent(a, PacksActivity::class.java)) }))
    }

    /** The answers' language (as the app's by default) and the offline method with its models, on this tab. */
    private fun buildTranslation(t: LinearLayout) {
        val names = Prefs.ANSWERS.map { c -> java.util.Locale(c).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() } }
        val answers = listOf("") + Prefs.ANSWERS
        t.addView(Rows.choice(a, a.getString(R.string.ans_lang), a.getString(R.string.ans_lang_d),
            listOf(a.getString(R.string.ans_lang_same)) + names, answers.indexOf(prefs.answerPick).coerceAtLeast(0)) {
            if (prefs.answerPick != answers[it]) { prefs.answerPick = answers[it]; a.refreshStatus(); refreshPacks() } }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(a, 4) })
        t.addView(Rows.heading(a, a.getString(R.string.mt_t)))
        pane = MethodPane(a, Ink.vertical(a).also { t.addView(it) })
    }

    private fun loadManifest() {
        refreshPacks()
        a.launch {
            val packs = prefs.packs
            val r = withContext(Dispatchers.IO) { runCatching { packs.list() } }
            // Offline: the list received last time, so what is installed can still be seen and removed.
            manifest = r.getOrNull() ?: withContext(Dispatchers.IO) { packs.cachedList() }.orEmpty()
            manifestError = r.exceptionOrNull()?.let { e -> if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException)
                a.getString(R.string.lang_no_net) else a.getString(R.string.lang_no_list, e.message ?: e.javaClass.simpleName) }
            refreshPacks()
        }
    }

    private var pane: MethodPane? = null

    /** The methods with what they need here; after a download, this device's speed. */
    private fun refreshMethod() { pane?.let { it.manifest = manifest; it.show(); if (!Downloads.busy) it.measure() } }

    /** The installed pairs: a box (the card uses it), its size, ✕; an update line when the release has newer files. */
    fun refreshPacks() {
        refreshMethod()
        if (!Downloads.busy) Downloads.result?.let { a.toast(it); Downloads.result = null }
        packList.removeAllViews()
        val packs = prefs.packs; val installed = packs.installed()
        val pairs = packs.installedPairs().sortedWith(compareBy({ it.second != prefs.answerCode }, { a.langName(it.first) }, { a.langName(it.second) }))
        val ticked = prefs.pairs ?: pairs.map { "${it.first}-${it.second}" }.toSet()
        if (pairs.isEmpty()) packList.addView(Rows.note(a, a.getString(R.string.packs_none)))
        pairs.forEach { pair ->
            val parts = Packs.needed(manifest, listOf(pair))
            val mb = (parts.filter { it.id in installed }.sumOf { it.size } / 1_000_000).toInt()
            val name = "${a.langName(pair.first)} → ${a.langName(pair.second)}"
            val newer = parts.any { installed[it.id].let { s -> s != null && s != it.sha256 } }
            val about = if (manifest.isEmpty()) "" else listOfNotNull(PacksActivity.quality(a, manifest, pair),
                a.getString(if (newer) R.string.pack_size_update else R.string.pack_size, mb)).joinToString(" · ")
            packList.addView(Rows.check(a, name, about, "${pair.first}-${pair.second}" in ticked, remove = {
                Rows.confirm(a, a.getString(R.string.pack_delete_q, name)) { removePair(pair) }
            }) { on -> prefs.pairs = (prefs.pairs ?: ticked).let { if (on) it + "${pair.first}-${pair.second}" else it - "${pair.first}-${pair.second}" }; a.refreshStatus() })
        }
        val updates = Packs.updates(manifest, installed) + if (manifest.isEmpty()) emptyList() else installed.keys.filter { id -> manifest.none { it.id == id } }.map { Packs.stale(it) }
        if (updates.isNotEmpty() && !Downloads.busy)
            packList.addView(Rows.buttons(a, a.getString(R.string.packs_update, (updates.sumOf { it.size } / 1_000_000).coerceAtLeast(1)) to {
                // A part gone from the release (old grammar packs) is removed; the others are downloaded again.
                Downloads.packs(a, updates.filter { c -> manifest.any { it.id == c.id } }, updates.filter { c -> manifest.none { it.id == c.id } }, a.getString(R.string.pack_done, a.getString(R.string.packs_title)))
            }))
        if (Downloads.busy) packList.addView(Rows.note(a, Downloads.status.ifEmpty { "…" }))
        manifestError?.let { e ->
            packList.addView(Rows.note(a, e))
            packList.addView(Rows.buttons(a, a.getString(R.string.lang_retry) to { loadManifest() }))
        }
    }

    private fun removePair(pair: Pair<String, String>) {
        // One download at a time: the running one is shown, the pair stays as it is.
        if (Downloads.busy) { a.showDownload(); return }
        val packs = prefs.packs; val all = packs.installedPairs()
        // Without the list (offline, first start) only the pair's dictionary is known to be its own.
        val freed = if (manifest.isEmpty()) listOf(Packs.stale("dict-${pair.first}-${pair.second}")) else Packs.freed(manifest, all, pair, prefs.mtMethod)
        prefs.pairs = prefs.pairs?.minus("${pair.first}-${pair.second}")
        Downloads.packs(a, emptyList(), freed, a.getString(R.string.pack_removed, "${a.langName(pair.first)} → ${a.langName(pair.second)}"))
    }

    // --- Other dictionaries ---
    private fun buildOther(t: LinearLayout) {
        t.addView(Rows.heading(a, a.getString(R.string.own_title), first = true))
        ownList = Ink.vertical(a); t.addView(ownList)
        t.addView(Rows.heading(a, a.getString(R.string.folder_title)))
        folderBox = Ink.vertical(a); t.addView(folderBox)
        t.addView(Rows.heading(a, a.getString(R.string.get_title)))
        // Each source can be switched off: its dictionaries are not listed for download.
        Catalog.SOURCES.forEach { src ->
            t.addView(Rows.check(a, src.title, a.getString(sourceAbout(src.id)), src.id !in prefs.sourcesOff) {
                prefs.sourcesOff = if (it) prefs.sourcesOff - src.id else prefs.sourcesOff + src.id })
        }
        t.addView(Rows.buttons(a, a.getString(R.string.get_dicts) to {
            if (Catalog.SOURCES.all { it.id in prefs.sourcesOff }) a.toast(a.getString(R.string.no_sources))
            else a.startActivity(Intent(a, CatalogActivity::class.java))
        }))
    }

    /** The other dictionaries (each with a box and ✕), the folder and the other apps' folders found on the device. */
    fun refreshOther(lib: Library, found: List<StarDict>, known: List<File>) {
        ownList.removeAllViews()
        val own = found.filter { !lib.isPack(it) }
        if (own.isEmpty()) ownList.addView(Rows.note(a, a.getString(R.string.own_none)))
        val folder = prefs.dictsDir; val where = a.short(folder)
        own.forEach { d ->
            val state = lib.status[d.id]?.let { if (it == "indexing") a.getString(R.string.st_indexing) else it }
            val words = d.props["wordcount"]?.toIntOrNull()?.let { a.getString(R.string.own_words, "%,d".format(it)) }
            ownList.addView(Rows.check(a, d.name, listOfNotNull(words, state).joinToString(" · "), d.id !in prefs.dictsOff, remove = {
                Rows.confirm(a, a.getString(R.string.dict_delete_q, d.name, where)) { Importer.delete(d.ifo, folder); a.refreshDicts() }
            }) { prefs.dictsOff = if (it) prefs.dictsOff - d.id else prefs.dictsOff + d.id })
        }
        folderBox.removeAllViews()
        folderBox.addView(Rows.info(a, null, where, a.getString(R.string.folder_d)))
        folderBox.addView(Rows.buttons(a, a.getString(R.string.change_folder) to { pickFolder() }, a.getString(R.string.import_file) to { pickFiles() }))
        folderBox.addView(Rows.note(a, a.getString(R.string.import_d)).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(a, 6) })
        known.filter { it.canonicalPath != folder.canonicalPath }.forEach { f ->
            folderBox.addView(Rows.buttons(a, a.getString(R.string.folder_found, a.short(f)) to { setFolder(f) }))
        }
    }

    private fun sourceAbout(id: String) = when (id) { "wikdict" -> R.string.cat_wikdict; "wiktionary" -> R.string.cat_wiktionary; else -> R.string.cat_freedict }

    private fun setFolder(f: File) {
        prefs.dictsDir = f; prefs.reload(); a.refreshDicts()
        a.launch { if (withContext(Dispatchers.IO) { StarDict.scan(f).isEmpty() }) a.toast(a.getString(R.string.folder_empty, a.short(f))) }
    }

    private fun pickFiles() = a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), FILES)

    /** A folder from the system picker; without one (some readers' firmware), its path typed in. */
    private fun pickFolder() = runCatching { a.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), FOLDER) }.onFailure {
        val path = a.edit(a.getString(R.string.folder_hint), prefs.dictsDir.path)
        InkDialog(a).title(R.string.change_folder).view(path).button(R.string.cancel)
            .button(android.R.string.ok) { File(path.text.toString().trim()).let { f -> if (f.isDirectory) setFolder(f) else a.toast(a.getString(R.string.failed, f.path)) } }.show()
    }

    fun onResult(requestCode: Int, data: Intent) {
        if (requestCode == FOLDER) { data.data?.let { treePath(it) }?.takeIf { it.isDirectory }?.let { setFolder(it) } ?: a.toast(a.getString(R.string.failed, data.data.toString())); return }
        if (requestCode != FILES) return
        val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        a.toast(a.getString(R.string.importing))
        a.launch {
            val target = prefs.dictsDir
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    // Loose .ifo/.idx/.dict files picked together make one dictionary; each archive is unpacked on its own.
                    val loose = File(a.cacheDir, "loose-" + System.nanoTime()).apply { mkdirs() }
                    try {
                        val all = uris.map { u ->
                            val name = displayName(u)
                            a.contentResolver.openInputStream(u)!!.use { input ->
                                if (Importer.isDictFile(name)) { File(loose, File(name).name).outputStream().use { input.copyTo(it) }; null }
                                else Importer.importStream(name, input, target, a.cacheDir)
                            }
                        }.filterNotNull() + Importer.collect(loose, target)
                        all.flatMap { it.added } to all.flatMap { it.already }
                    } finally { loose.deleteRecursively() }
                }
            }
            r.fold({ (added, already) -> a.toast(listOfNotNull(a.getString(R.string.imported, added.size),
                already.takeIf { it.isNotEmpty() }?.let { a.getString(R.string.already_there, it.joinToString()) }).joinToString("\n")) },
                { a.toast(a.getString(R.string.import_failed, it.message)) })
            a.refreshDicts()
        }
    }

    private fun displayName(u: Uri): String = a.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: u.lastPathSegment.orEmpty()

    /** A picked tree "primary:koreader/data/dict" as a path (the app reads shared storage directly). */
    private fun treePath(u: Uri): File? = runCatching {
        val id = android.provider.DocumentsContract.getTreeDocumentId(u)
        val (vol, rel) = id.substringBefore(':') to id.substringAfter(':', "")
        File(if (vol == "primary") Environment.getExternalStorageDirectory() else File("/storage", vol), rel)
    }.getOrNull()


    companion object {
        const val FILES = 1; const val FOLDER = 2
        /** Where other apps keep StarDict dictionaries: KOReader, ColorDict, GoldenDict. */
        val KNOWN = listOf("koreader/data/dict", "dictdata", "GoldenDict")
    }
}
