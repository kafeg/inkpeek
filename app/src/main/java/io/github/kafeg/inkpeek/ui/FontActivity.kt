package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The card's font (Card → Appearance): the sample and two tabs stay on top (Android's fonts, yours), the list scrolls. */
class FontActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var sample: TextView
    private lateinit var list: LinearLayout
    private lateinit var tabs: LinearLayout
    private var tab = ""
    private var loaded = false
    private var system: List<Fonts.Item> = emptyList()
    private var yours: List<Fonts.Item> = emptyList()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        Ink.theme(this)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        col.addView(Rows.header(this, getString(R.string.font_t)))
        // How the card's text looks in the chosen font, at the card's text size.
        sample = Ink.text(this, "", prefs.cardText.toFloat(), html = true).apply {
            background = Ink.border(this@FontActivity, 3, 8); val q = Ink.dp(this@FontActivity, 12); setPadding(q, q, q, q) }
        col.addView(sample, Ink.lp().apply { topMargin = Ink.dp(this@FontActivity, 6) })
        tabs = Ink.vertical(this); col.addView(tabs, Ink.lp().apply { topMargin = Ink.dp(this@FontActivity, 10) })
        list = Ink.vertical(this)
        col.addView(ScrollView(this).apply { addView(list) }, Ink.lp(h = 0, weight = 1f))
        setContentView(col)
        // The tab of the font in use: one of yours opens on Yours.
        tab = if (prefs.cardFont.isNotEmpty() && !prefs.cardFont.startsWith("/system/")) "yours" else "device"
        draw()
        showSample(); refresh()
    }

    override fun onDestroy() { cancel(); super.onDestroy() }

    /** The fonts are read off the main thread: checking each one's letters opens its file. */
    private fun refresh() = launch {
        withContext(Dispatchers.IO) { system = Fonts.system(); yours = Fonts.yours() }
        loaded = true; draw()
    }

    private fun draw() {
        val now = prefs.cardFont
        showSample()
        tabs.removeAllViews()
        tabs.addView(Ink.subTabs(this, listOf("device" to getString(R.string.font_device), "yours" to getString(R.string.font_yours)), tab, 16f) {
            tab = it; draw() })
        list.removeAllViews()
        // Reading the fonts takes a moment the first time (each one's letters are checked).
        if (!loaded) { list.addView(Rows.note(this, getString(R.string.font_loading))); return }
        if (tab == "device") {
            list.addView(Rows.note(this, getString(R.string.font_device_d)))
            row(Fonts.Item("", getString(R.string.font_default), getString(R.string.font_default_d), ""), now)
            system.forEach { row(it, now) }
        } else {
            list.addView(Rows.note(this, getString(if (yours.isEmpty()) R.string.font_none else R.string.font_yours_d)))
            yours.forEach { row(it, now) }
            list.addView(Rows.buttons(this, getString(R.string.font_file) to { pick() }))
        }
    }

    private fun showSample() {
        sample.text = Ink.text(this, SAMPLE, prefs.cardText.toFloat(), html = true).text
        sample.typeface = Fonts.face(prefs.cardFont) ?: android.graphics.Typeface.DEFAULT
    }

    /** A font in itself with "Haus, дом", where it is from (and the letters it lacks); ✓ on the one in use; ✕ for a copied one. */
    private fun row(f: Fonts.Item, now: String) {
        val face = Fonts.face(f.id) ?: android.graphics.Typeface.DEFAULT
        val r = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val texts = Ink.vertical(this)
        texts.addView(Ink.text(this, (if (f.id == now) "✓ " else "") + f.name + " · Haus, дом", 19f).apply { typeface = face })
        val about = listOfNotNull(f.where, f.missing.takeIf { it.isNotEmpty() }?.let { getString(R.string.font_missing, it) }).joinToString(" · ")
        texts.addView(Ink.text(this, about, 13f))
        r.addView(texts, Ink.lp(0, weight = 1f))
        if (f.where == "InkPeek/fonts") r.addView(Ink.button(this, "✕", 17f) {
            Rows.confirm(this, getString(R.string.font_delete_q, f.name)) {
                File(f.id).delete(); if (prefs.cardFont == f.id) prefs.cardFont = ""; refresh() }
        })
        r.setOnClickListener { prefs.cardFont = f.id; draw() }
        texts.setOnClickListener { prefs.cardFont = f.id; draw() }
        list.addView(r, Ink.lp().apply { topMargin = Ink.dp(this@FontActivity, 10) })
    }

    private fun pick() = runCatching {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), FILE)
    }

    /** A chosen font file: copied to InkPeek/fonts and used; one InkPeek cannot read is refused. */
    @Deprecated("Plain Activity result: no AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != FILE || resultCode != RESULT_OK) return
        val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null } ?: "font.ttf"
        if (name.substringAfterLast('.').lowercase() !in setOf("ttf", "otf")) { Ink.toast(this, getString(R.string.font_bad, name)); return }
        val f = runCatching { contentResolver.openInputStream(uri)!!.use { Fonts.keep(prefs.home, name, it) } }
            .getOrElse { Ink.toast(this, getString(R.string.failed, it.message)); return }
        if (Fonts.face(f.path) == null) { f.delete(); Ink.toast(this, getString(R.string.font_bad, name)); return }
        prefs.cardFont = f.path; refresh()
    }

    companion object {
        private const val FILE = 1
        private const val SAMPLE = "<b>das Haus</b> (Häuser) — дом, здание; house, building<br><i>In dem Haus haben wir gewohnt.</i>"
    }
}
