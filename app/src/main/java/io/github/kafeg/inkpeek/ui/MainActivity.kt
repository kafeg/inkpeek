package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.stardict.Importer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings and dictionaries on one screen. */
class MainActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var dictList: LinearLayout
    private lateinit var status: android.widget.TextView
    private val sp = 18f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(Ink.text(this, "InkPeek", 26f, bold = true))
        status = Ink.text(this, "", 14f); col.addView(status)

        section(col, "Try it")
        val probe = edit("Word or phrase", "")
        col.addView(probe)
        col.addView(Ink.button(this, "Look up", sp) {
            startActivity(Intent(this, CardActivity::class.java).setAction("colordict.intent.action.SEARCH")
                .putExtra("EXTRA_QUERY", probe.text.toString()))
        }, Ink.lp().apply { topMargin = 8 })

        section(col, "Dictionaries")
        dictList = Ink.vertical(this); col.addView(dictList)
        val row = Ink.horizontal(this)
        row.addView(Ink.button(this, "Import file", sp) { pickFiles() }, Ink.lp(0, weight = 1f))
        row.addView(Ink.button(this, "Get dictionaries", sp) { startActivity(Intent(this, CatalogActivity::class.java)) },
            Ink.lp(0, weight = 1f).apply { leftMargin = Ink.dp(this@MainActivity, 8) })
        col.addView(row, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })
        col.addView(Ink.text(this, "Folder: ${prefs.dictsDir}", 14f), Ink.lp().apply { topMargin = 16 })
        if (!Environment.isExternalStorageManager())
            col.addView(Ink.button(this, "Allow access to all files", sp) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }, Ink.lp().apply { topMargin = 8 })

        section(col, "AI")
        col.addView(switch("Use AI", prefs.aiOn) { prefs.aiOn = it })
        col.addView(switch("Phrases: answer with AI at once", prefs.phraseAutoAi) { prefs.phraseAutoAi = it })
        val providers = Provider.entries
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, providers.map { it.title })
            setSelection(providers.indexOf(prefs.provider))
        }
        col.addView(spinner)
        val key = edit("API key", "", secret = true)
        val model = edit("Model", "")
        col.addView(key); col.addView(model)
        fun load(p: Provider) {
            key.setText(prefs.key(p)); model.setText(prefs.model(p))
            key.hint = if (p == Provider.ANTHROPIC) "API key (empty: read ${prefs.keyFile})" else "API key"
        }
        load(prefs.provider)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: android.widget.AdapterView<*>?, v: android.view.View?, i: Int, id: Long) {
                prefs.provider = providers[i]; load(providers[i])
            }
            override fun onNothingSelected(a: android.widget.AdapterView<*>?) {}
        }
        col.addView(Ink.button(this, "Save AI settings", sp) {
            prefs.setKey(prefs.provider, key.text.toString()); prefs.setModel(prefs.provider, model.text.toString())
            toast("Saved")
        }, Ink.lp().apply { topMargin = 8 })
        col.addView(Ink.button(this, "Choose model…", sp) { chooseModel(model) }, Ink.lp().apply { topMargin = 8 })
        col.addView(Ink.text(this, "Answer language", 14f), Ink.lp().apply { topMargin = 16 })
        col.addView(edit("Language", prefs.answerLang) { prefs.answerLang = it })

        section(col, "Card")
        col.addView(Ink.text(this, "Text size (sp)", 14f))
        col.addView(edit("Size", prefs.fontSp.toString(), number = true) { it.toIntOrNull()?.let { v -> prefs.fontSp = v.coerceIn(12, 40) } })
        col.addView(switch("Dim the page behind", prefs.dim) { prefs.dim = it })
        val pos = listOf("center", "top", "bottom", "reader")
        col.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, pos.map { "Position: $it" })
            setSelection(pos.indexOf(prefs.position).coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: android.widget.AdapterView<*>?, v: android.view.View?, i: Int, id: Long) { prefs.position = pos[i] }
                override fun onNothingSelected(a: android.widget.AdapterView<*>?) {}
            }
        })
    }

    override fun onResume() { super.onResume(); refreshDicts() }
    override fun onDestroy() { cancel(); super.onDestroy() }

    private fun section(col: LinearLayout, title: String) =
        col.addView(Ink.text(this, title, 20f, bold = true), Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 22) })

    private fun edit(hint: String, value: String, secret: Boolean = false, number: Boolean = false, save: ((String) -> Unit)? = null) =
        EditText(this).apply {
            this.hint = hint; setText(value); textSize = sp; setSingleLine()
            inputType = when { secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                number -> InputType.TYPE_CLASS_NUMBER; else -> InputType.TYPE_CLASS_TEXT }
            if (save != null) setOnFocusChangeListener { _, has -> if (!has) save(text.toString()) }
        }

    private fun switch(label: String, value: Boolean, save: (Boolean) -> Unit) = Switch(this).apply {
        text = label; textSize = sp; isChecked = value; setOnCheckedChangeListener { _, v -> save(v) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun refreshDicts() {
        launch {
            val dicts = withContext(Dispatchers.IO) { prefs.library().all(prefs.order) }
            prefs.order = dicts.map { it.id }
            dictList.removeAllViews()
            if (dicts.isEmpty()) dictList.addView(Ink.text(this@MainActivity, "No dictionaries yet.", 16f))
            dicts.forEachIndexed { i, d ->
                val r = Ink.horizontal(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                r.addView(CheckBox(this@MainActivity).apply {
                    text = d.name; textSize = 16f; isChecked = d.id !in prefs.disabled
                    setOnCheckedChangeListener { _, on -> prefs.disabled = if (on) prefs.disabled - d.id else prefs.disabled + d.id }
                }, Ink.lp(0, weight = 1f))
                r.addView(Ink.button(this@MainActivity, "↑", 16f) { move(i, -1) })
                r.addView(Ink.button(this@MainActivity, "↓", 16f) { move(i, 1) })
                r.addView(Ink.button(this@MainActivity, "✕", 16f) { delete(d.name, d.ifo.parentFile!!) })
                dictList.addView(r)
            }
            status.text = "${dicts.size} dictionaries · AI: ${prefs.provider.title}"
            withContext(Dispatchers.IO) { dicts.filter { !it.ready }.forEach { d -> runCatching { d.prepare() } } }
        }
    }

    private fun move(i: Int, by: Int) {
        val o = prefs.order.toMutableList(); val j = i + by
        if (j !in o.indices) return
        o[i] = o[j].also { o[j] = o[i] }; prefs.order = o; refreshDicts()
    }

    private fun delete(name: String, dir: java.io.File) = AlertDialog.Builder(this).setMessage("Delete «$name»?")
        .setPositiveButton("Delete") { _, _ -> dir.deleteRecursively(); refreshDicts() }.setNegativeButton("Cancel", null).show()

    private fun pickFiles() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 1)

    @Deprecated("Activity result API is not used to stay dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK || data == null) return
        val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        status.text = "Importing…"
        launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val loose = java.io.File(cacheDir, "loose-" + System.nanoTime()).apply { mkdirs() }
                    val dirs = uris.flatMap { u ->
                        val name = displayName(u)
                        contentResolver.openInputStream(u)!!.use { input ->
                            if (name.endsWith(".ifo") || name.contains(".idx") || name.contains(".dict") || name.contains(".syn"))
                                java.io.File(loose, name).outputStream().use { input.copyTo(it) }.let { emptyList() }
                            else Importer.importStream(name, input, prefs.dictsDir, cacheDir)
                        }
                    } + Importer.collect(loose, prefs.dictsDir)
                    loose.deleteRecursively(); dirs.size
                }
            }
            r.fold({ toast("Imported: $it") }, { toast("Import failed: ${it.message}") })
            refreshDicts()
        }
    }

    private fun displayName(u: Uri): String = contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: u.lastPathSegment.orEmpty()

    private fun chooseModel(target: EditText) {
        val p = prefs.provider; status.text = "Loading models…"
        launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ai.models(p, prefs.key(p).ifBlank { prefs.effectiveKey(p) }) } }
            status.text = ""
            r.fold({ list ->
                AlertDialog.Builder(this@MainActivity).setTitle(p.title).setItems(list.toTypedArray()) { _, i ->
                    target.setText(list[i]); prefs.setModel(p, list[i])
                }.show()
            }, { toast("Models: ${it.message}") })
        }
    }
}
