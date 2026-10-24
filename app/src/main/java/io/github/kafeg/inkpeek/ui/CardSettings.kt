package io.github.kafeg.inkpeek.ui

import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R

/** Card: Opening (what a word and a phrase open on, the controls), Article (the order, what the packs add), Look. */
class CardSettings(private val a: MainActivity) {
    private val prefs get() = a.prefs

    fun build(t: LinearLayout) {
        val opening = Ink.vertical(a); val article = Ink.vertical(a); val look = Ink.vertical(a)
        a.tabPick["card"] = Rows.subTabs(a, t, listOf(Triple("open", a.getString(R.string.card_open), opening), Triple("article", a.getString(R.string.lang_article), article),
            Triple("look", a.getString(R.string.card_look), look)), a.open["card"] ?: "open") { a.open["card"] = it }
        buildOpening(opening); buildArticle(article); buildLook(look)
    }

    private fun first(v: android.view.View) = v.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(a, 4) }

    private fun buildOpening(t: LinearLayout) {
        t.addView(first(Rows.choice(a, a.getString(R.string.card_dict_t), a.getString(R.string.card_dict_d),
            listOf(a.getString(R.string.dict_short), a.getString(R.string.dict_full)), if (prefs.cardFull) 1 else 0) { prefs.cardFull = it == 1 }))
        // What a phrase opens on: offline or AI translation, word by word, an AI tab (AI then asks on every opening).
        val opens = listOf("offline" to R.string.open_offline, "ai" to R.string.open_ai, "dict" to R.string.open_dict,
            "analysis" to R.string.a_analysis, "context" to R.string.a_context)
        t.addView(Rows.choice(a, a.getString(R.string.phrase_open), a.getString(R.string.phrase_open_d), opens.map { a.getString(it.second) },
            opens.indexOfFirst { it.first == prefs.phraseOpen }.coerceAtLeast(0)) { prefs.phraseOpen = opens[it].first })
        t.addView(Rows.heading(a, a.getString(R.string.controls_t)))
        t.addView(Rows.check(a, a.getString(R.string.look_bar), a.getString(R.string.look_bar_d), prefs.cardBar) { prefs.cardBar = it })
        t.addView(Rows.check(a, a.getString(R.string.look_save), a.getString(R.string.look_save_d), prefs.cardSave) { prefs.cardSave = it })
    }

    /** The dictionaries' order, then what the card takes from InkPeek's language packs, one row per part. */
    private fun buildArticle(t: LinearLayout) {
        t.addView(first(Rows.info(a, null, a.getString(R.string.order_t), a.getString(R.string.order_d), page = true) {
            a.startActivity(android.content.Intent(a, OrderActivity::class.java)) }))
        t.addView(Rows.heading(a, a.getString(R.string.pack_use)))
        t.addView(Rows.check(a, a.getString(R.string.pack_dicts_t), a.getString(R.string.pack_dicts_d), prefs.packDicts) { prefs.packDicts = it; a.refreshStatus() })
        t.addView(Rows.check(a, a.getString(R.string.pack_gram_t), a.getString(R.string.pack_gram_d), prefs.packGrammar) { prefs.packGrammar = it })
        listOf(Triple("rel", R.string.sec_rel, R.string.sec_rel_d), Triple("syn", R.string.sec_syn, R.string.sec_syn_d),
            Triple("expr", R.string.sec_expr, R.string.sec_expr_d), Triple("forms", R.string.sec_forms, R.string.sec_forms_d),
            Triple("ety", R.string.sec_ety, R.string.sec_ety_d)).forEach { (k, title, about) ->
            t.addView(Rows.check(a, a.getString(title), a.getString(about), prefs.packSection(k)) { prefs.setPackSection(k, it) }) }
    }

    private fun buildLook(t: LinearLayout) {
        val heights = listOf(0, 50, 60, 70, 80, 90)
        t.addView(first(Rows.choice(a, a.getString(R.string.look_height), a.getString(R.string.look_height_d),
            heights.map { if (it == 0) a.getString(R.string.look_height_auto) else "$it %" }, heights.indexOf(prefs.cardHeight).coerceAtLeast(0)) { prefs.cardHeight = heights[it] }))
        val widths = listOf(70, 80, 92, 100)
        t.addView(Rows.choice(a, a.getString(R.string.look_width), a.getString(R.string.look_width_d), widths.map { "$it %" },
            widths.indexOf(prefs.cardWidth).coerceAtLeast(0)) { prefs.cardWidth = widths[it] })
        val places = listOf("center", "top", "bottom", "reader")
        t.addView(Rows.choice(a, a.getString(R.string.look_place), a.getString(R.string.look_place_d),
            listOf(R.string.place_center, R.string.place_top, R.string.place_bottom, R.string.place_reader).map { a.getString(it) },
            places.indexOf(prefs.cardPlace).coerceAtLeast(0)) { prefs.cardPlace = places[it] })
        val sizes = listOf(16, 18, 20, 22, 24, 26)
        t.addView(Rows.choice(a, a.getString(R.string.look_text), a.getString(R.string.look_text_d), sizes.map { "$it" },
            sizes.indexOf(prefs.cardText).coerceAtLeast(0)) { prefs.cardText = sizes[it] })
        val dims = listOf(0, 10, 25, 40, 60)
        t.addView(Rows.choice(a, a.getString(R.string.look_dim), a.getString(R.string.look_dim_d), dims.map { if (it == 0) a.getString(R.string.dim_off) else "$it %" },
            dims.indexOf(prefs.cardDim).coerceAtLeast(0)) { prefs.cardDim = dims[it] })
        fontRow(t)
    }

    private lateinit var fontBox: LinearLayout

    /** The card's font: its name here, the choice on its own page (FontActivity). */
    private fun fontRow(t: LinearLayout) { fontBox = Ink.vertical(a); t.addView(fontBox); refreshFont() }

    /** After the font page: the name of the one in use. */
    fun refreshFont() {
        if (!::fontBox.isInitialized) return
        fontBox.removeAllViews()
        fontBox.addView(Rows.info(a, null, a.getString(R.string.look_font), Fonts.label(prefs.cardFont, a.getString(R.string.font_default)), page = true) {
            a.startActivity(android.content.Intent(a, FontActivity::class.java)) })
    }

}
