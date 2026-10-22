package io.github.kafeg.inkpeek.ui

import android.widget.LinearLayout
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Action

/** Card: Main (language, theme, dictionaries, phrases, icons), Appearance (size, place, text, font), Icons (what each one does). */
class CardSettings(private val a: MainActivity) {
    private val prefs get() = a.prefs
    private var aiRow: android.view.View? = null

    /** The AI icons' switch works only while an AI provider is chosen (AI tab). */
    fun syncAi() { aiRow?.let { Rows.enable(it, prefs.aiOn) } }

    fun build(t: LinearLayout) {
        val main = Ink.vertical(a); val look = Ink.vertical(a); val icons = Ink.vertical(a)
        Rows.subTabs(a, t, listOf(Triple("main", a.getString(R.string.card_main), main), Triple("look", a.getString(R.string.card_look), look),
            Triple("icons", a.getString(R.string.card_icons), icons)), a.open["card"] ?: "main") { a.open["card"] = it }
        buildMain(main); buildLook(look); buildIcons(icons)
    }

    private fun first(v: android.view.View) = v.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(a, 4) }

    private fun buildMain(t: LinearLayout) {
        val codes = listOf("") + Prefs.ANSWERS
        val names = listOf(a.getString(R.string.ui_lang_system)) + Prefs.ANSWERS.map { c ->
            java.util.Locale(c).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() } }
        t.addView(first(Rows.choice(a, a.getString(R.string.ui_lang), a.getString(R.string.ui_lang_d), names, codes.indexOf(prefs.uiLang).coerceAtLeast(0)) {
            if (prefs.uiLang != codes[it]) { prefs.uiLang = codes[it]; a.recreate() } }))
        // The theme of the whole app: the settings are made again in it.
        val themes = listOf("light", "dark", "system")
        t.addView(Rows.choice(a, a.getString(R.string.look_theme), a.getString(R.string.look_theme_d),
            listOf(R.string.theme_light, R.string.theme_dark, R.string.theme_system).map { a.getString(it) },
            themes.indexOf(prefs.theme).coerceAtLeast(0)) { if (prefs.theme != themes[it]) { prefs.theme = themes[it]; a.recreate() } })
        t.addView(Rows.choice(a, a.getString(R.string.card_dict_t), a.getString(R.string.card_dict_d),
            listOf(a.getString(R.string.dict_short), a.getString(R.string.dict_full)), if (prefs.cardFull) 1 else 0) { prefs.cardFull = it == 1 })
        t.addView(Rows.info(a, null, a.getString(R.string.order_t), a.getString(R.string.order_d), page = true) {
            a.startActivity(android.content.Intent(a, OrderActivity::class.java)) })
        t.addView(Rows.check(a, a.getString(R.string.phrase_ai), a.getString(R.string.phrase_ai_d), prefs.phraseAutoAi) { prefs.phraseAutoAi = it })
        t.addView(Rows.check(a, a.getString(R.string.look_bar), a.getString(R.string.look_bar_d), prefs.cardBar) { prefs.cardBar = it })
        aiRow = Rows.check(a, a.getString(R.string.card_ai_t), a.getString(R.string.card_ai_d), prefs.cardAi) { prefs.cardAi = it }
        t.addView(aiRow); syncAi()
        t.addView(Rows.check(a, a.getString(R.string.look_save), a.getString(R.string.look_save_d), prefs.cardSave) { prefs.cardSave = it })
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

    /** Help: each icon of the card, what it does. */
    private fun buildIcons(t: LinearLayout) {
        val rows = listOf(Triple(R.drawable.ic_dictionary, a.getString(R.string.tab_dictionary), a.getString(R.string.icon_dict_d)),
            Triple(R.drawable.ic_translate, a.getString(R.string.tab_translate), a.getString(R.string.icon_translate_d))) +
            Action.ALL.map { x -> Triple(if (x.id == "analysis") R.drawable.ic_analysis else R.drawable.ic_context, Ink.label(a, x) + " · AI", Ink.description(a, x)) } +
            listOf(Triple(R.drawable.ic_star, a.getString(R.string.save_word), a.getString(R.string.icon_save_d)),
                Triple(R.drawable.ic_words, a.getString(R.string.tab_words), a.getString(R.string.icon_words_d)),
                Triple(R.drawable.ic_back, a.getString(R.string.back), a.getString(R.string.icon_back_d)),
                Triple(R.drawable.ic_close, a.getString(R.string.close), a.getString(R.string.icon_close_d)),
                Triple(R.drawable.ic_copy, a.getString(R.string.hold_t), a.getString(R.string.hold_d)))
        rows.forEachIndexed { i, (icon, title, about) -> t.addView(Rows.info(a, icon, title, about, lines = 3).let { if (i == 0) first(it) else it }) }
    }
}
