package io.github.kafeg.inkpeek.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.Gravity
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import java.io.File

/** ⚙ The app: its language, the theme of the whole app, updates, file access. */
class AppPage(private val a: MainActivity) {
    private val prefs get() = a.prefs

    fun build(t: LinearLayout) {
        t.addView(Rows.header(a, a.getString(R.string.app_t)) { a.closePage() })
        val codes = listOf("") + Prefs.ANSWERS
        val names = listOf(a.getString(R.string.ui_lang_system)) + Prefs.ANSWERS.map { c ->
            Ink.ownName(c) }
        // A new language or theme makes the settings again (this page reopens).
        t.addView(Rows.choice(a, a.getString(R.string.ui_lang), a.getString(R.string.ui_lang_d), names, codes.indexOf(prefs.uiLang).coerceAtLeast(0)) {
            if (prefs.uiLang != codes[it]) { prefs.uiLang = codes[it]; a.recreate() } })
        val themes = listOf("light", "dark", "system")
        t.addView(Rows.choice(a, a.getString(R.string.look_theme), a.getString(R.string.look_theme_d),
            listOf(R.string.theme_light, R.string.theme_dark, R.string.theme_system).map { a.getString(it) },
            themes.indexOf(prefs.theme).coerceAtLeast(0)) { if (prefs.theme != themes[it]) { prefs.theme = themes[it]; a.recreate() } })
        t.addView(Rows.heading(a, a.getString(R.string.updates_t)))
        t.addView(Rows.check(a, a.getString(R.string.check_daily_t), a.getString(R.string.check_daily_d), prefs.updateCheck) { prefs.updateCheck = it })
        t.addView(Rows.buttons(a, a.getString(R.string.check_now) to { About.show(a) }))
        t.addView(Rows.heading(a, a.getString(R.string.files_t)))
        if (Environment.isExternalStorageManager()) t.addView(Rows.info(a, null, a.getString(R.string.files_ok), a.getString(R.string.files_d)))
        else t.addView(Rows.buttons(a, a.getString(R.string.allow_files, prefs.home.path) to {
            a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${a.packageName}"))) }))
    }
}

/** ? Help: the topics, each on its page with a way to its place in the app ("Go to: Card → Opening"). Back from a topic returns to the list. */
class HelpPage(private val a: MainActivity, private val box: LinearLayout) {
    private var inTopic = false

    /** [go]: the section, its sub-tab's key and title (null: the section as it is). */
    private class Topic(val title: Int, val go: Triple<Int, String?, Int?>?, val body: HelpPage.(LinearLayout) -> Unit)

    private val topics = listOf(
        Topic(R.string.ht_readers, null) { t ->
            para(t, R.string.help_fb); para(t, R.string.help_ko)
            t.addView(Rows.buttons(a, a.getString(R.string.help_ko_btn) to { koreader() }))
            para(t, R.string.help_readers_other)
        },
        Topic(R.string.ht_card, Triple(R.string.tab_card, "open", R.string.card_open)) { t ->
            item(t, R.string.hc_phrase_t, R.string.hc_phrase); item(t, R.string.hc_head_t, R.string.hc_head); item(t, R.string.hc_article_t, R.string.hc_article)
            t.addView(Rows.info(a, null, a.getString(R.string.hc_ai_t), a.getString(R.string.hc_ai, a.getString(R.string.prefetch_t)), lines = 40))
            t.addView(Rows.heading(a, a.getString(R.string.controls_t)))
            val rows = listOf(Triple(R.drawable.ic_dictionary, a.getString(R.string.tab_dictionary), a.getString(R.string.icon_dict_d)),
                Triple(R.drawable.ic_translate, a.getString(R.string.tab_translate), a.getString(R.string.icon_translate_d))) +
                listOf(Triple(R.drawable.ic_analysis, a.getString(R.string.tab_ai_card) + " · AI", a.getString(R.string.icon_ai_d))) +
                listOf(Triple(R.drawable.ic_star, a.getString(R.string.save_word), a.getString(R.string.icon_save_d)),
                    Triple(R.drawable.ic_words, a.getString(R.string.tab_words), a.getString(R.string.icon_words_d)),
                    Triple(R.drawable.ic_back, a.getString(R.string.back), a.getString(R.string.icon_back_d)),
                    Triple(R.drawable.ic_close, a.getString(R.string.close), a.getString(R.string.icon_close_d)))
            rows.forEach { (icon, title, about) -> t.addView(Rows.info(a, icon, title, about, lines = 4)) }
        },
        Topic(R.string.ht_select, null) { t ->
            item(t, R.string.hs_word_t, R.string.hs_word); item(t, R.string.hs_para_t, R.string.hs_para)
            item(t, R.string.hs_menu_t, R.string.hs_menu); item(t, R.string.hs_rows_t, R.string.hs_rows)
        },
        Topic(R.string.packs_title, Triple(R.string.tab_dicts, "packs", R.string.lang_packs)) { t ->
            item(t, R.string.help_dict_t, R.string.help_dict); item(t, R.string.help_gram_t, R.string.help_gram)
            item(t, R.string.help_build_t, R.string.help_build); para(t, R.string.help_labels)
        },
        Topic(R.string.mt_t, Triple(R.string.tab_dicts, "translation", R.string.lang_translation)) { t ->
            item(t, R.string.help_mt_t, R.string.help_mt); item(t, R.string.mt_off_t, R.string.hm_off); item(t, R.string.help_lid_t, R.string.help_lid)
        },
        Topic(R.string.own_title, Triple(R.string.tab_dicts, "other", R.string.lang_own_tab)) { t ->
            item(t, R.string.help_other_t, R.string.help_other); item(t, R.string.help_formats_t, R.string.help_formats); item(t, R.string.help_folder_t, R.string.help_folder)
        },
        Topic(R.string.tab_ai, Triple(R.string.tab_ai, "connect", R.string.ai_connect)) { t ->
            item(t, R.string.aih_what_t, R.string.aih_what); item(t, R.string.prompt_quick_t, R.string.aih_translate)
            item(t, R.string.a_analysis, R.string.aih_analysis); item(t, R.string.a_context, R.string.aih_context)
            t.addView(Rows.info(a, null, a.getString(R.string.hc_ai_t), a.getString(R.string.hc_ai, a.getString(R.string.prefetch_t)), lines = 40))
            item(t, R.string.aih_keys_t, R.string.aih_keys); item(t, R.string.aih_local_t, R.string.aih_local); item(t, R.string.aih_free_t, R.string.aih_free)
        },
        Topic(R.string.aih_send_t, null) { t -> para(t, R.string.aih_send) },
        Topic(R.string.ht_notes, Triple(R.string.tab_words, null, null)) { t -> para(t, R.string.help_notes) })

    fun list() {
        inTopic = false; box.removeAllViews()
        box.addView(Rows.header(a, a.getString(R.string.help_t)) { a.closePage() })
        topics.forEachIndexed { i, topic -> box.addView(Rows.info(a, null, a.getString(topic.title), "", lines = 1, page = true) { open(i) }) }
        box.addView(Rows.info(a, null, a.getString(R.string.ht_about), "", lines = 1, page = true) { About.show(a) })
    }

    /** From a topic back to the list; false on the list (the page closes). */
    fun back(): Boolean = inTopic.also { if (it) list() }

    private fun open(i: Int) {
        inTopic = true; box.removeAllViews()
        val topic = topics[i]
        box.addView(Rows.header(a, a.getString(topic.title)) { list() })
        topic.body(this, box)
        topic.go?.let { (section, tab, name) -> val to = listOfNotNull(section, name).joinToString(" → ") { a.getString(it) }
            box.addView(Rows.buttons(a, a.getString(R.string.help_go, to) to { a.goTo(section, tab) })) }
    }

    private fun para(t: LinearLayout, text: Int) = t.addView(Ink.text(a, a.getString(text), 16f), Ink.lp().apply { topMargin = Ink.dp(a, 10) })
    private fun item(t: LinearLayout, title: Int, text: Int) = t.addView(Rows.info(a, null, a.getString(title), a.getString(text), lines = 40))

    /** KOReader calls only the dictionary apps in koreader/dictionaries.lua: InkPeek is added there, its other entries stay. */
    private fun koreader() {
        val f = File(Environment.getExternalStorageDirectory(), "koreader/dictionaries.lua")
        runCatching {
            val new = Koreader.withInkPeek(if (f.exists()) f.readText() else null)
            if (new == null) a.toast(a.getString(R.string.help_ko_have))
            else { f.parentFile?.mkdirs(); f.writeText(new); a.toast(a.getString(R.string.help_ko_done)) }
        }.onFailure { a.toast(a.getString(R.string.help_ko_fail, it.message ?: it.javaClass.simpleName)) }
    }
}

/** KOReader's list of external dictionary apps (koreader/dictionaries.lua: "return { {…}, … }"). */
object Koreader {
    private const val ENTRY = "    { \"InkPeek\", \"InkPeek\", false, \"io.github.kafeg.inkpeek\", \"text\" },"

    /** The file with InkPeek in its list, the others kept; a new file when there is none; null when InkPeek is there. */
    fun withInkPeek(old: String?): String? = when {
        old == null -> "return {\n$ENTRY\n}\n"
        "io.github.kafeg.inkpeek" in old -> null
        else -> Regex("""return\s*\{""").find(old)?.let { m -> old.substring(0, m.range.last + 1) + "\n" + ENTRY + old.substring(m.range.last + 1) }
            ?: error("no \"return {\" in it")
    }
}
