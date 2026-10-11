package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.stardict.cleanQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The pop-up card: dictionary entries and AI actions for the selected text. */
class CardActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private var aiBlock: TextView? = null
    private var query = ""
    private val size get() = prefs.fontSp.toFloat()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        query = cleanQuery(textOf(intent).orEmpty()).take(500)
        if (query.isEmpty()) { finish(); return }
        build()
        placeWindow()
        val phrase = query.contains(' ')
        if (phrase && prefs.aiOn && prefs.phraseAutoAi) ask(prefs.actions.first()) else lookup(phrase)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    override fun onDestroy() { cancel(); super.onDestroy() }

    private fun textOf(i: Intent): String? = when (i.action) {
        "colordict.intent.action.SEARCH" -> i.getStringExtra("EXTRA_QUERY")
        Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
        else -> i.getStringExtra("EXTRA_QUERY") ?: i.getStringExtra(Intent.EXTRA_TEXT)
    }

    private fun build() {
        val pad = Ink.dp(this, 14)
        val root = Ink.vertical(this).apply { background = Ink.border(this@CardActivity, 3, 10); setPadding(pad, pad, pad, pad) }
        val head = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ink.text(this, query, size * 1.3f, bold = true), Ink.lp(0, weight = 1f))
        head.addView(Ink.button(this, "✕", size) { finish() }, Ink.lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(head)
        body = Ink.vertical(this)
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = true; addView(body) }
        root.addView(scroll, Ink.lp(h = 0, weight = 1f).apply { topMargin = Ink.dp(this@CardActivity, 8) })
        if (prefs.aiOn) {
            val row = Ink.horizontal(this)
            prefs.actions.forEachIndexed { i, a ->
                row.addView(Ink.button(this, a.name, size * 0.8f) { ask(a) },
                    Ink.lp(0, weight = 1f).apply { if (i > 0) leftMargin = Ink.dp(this@CardActivity, 6) })
            }
            root.addView(row, Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 10) })
        }
        setContentView(root)
    }

    private fun placeWindow() {
        val dm = resources.displayMetrics
        window.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.62).toInt())
        window.setGravity(when (prefs.position) {
            "top" -> Gravity.TOP; "bottom" -> Gravity.BOTTOM
            "reader" -> intent.getIntExtra("EXTRA_GRAVITY", Gravity.CENTER)
            else -> Gravity.CENTER
        })
        if (prefs.dim) { window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(0.25f) }
        else window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setWindowAnimations(0)
    }

    private fun note(s: String) = body.addView(Ink.text(this, s, size * 0.85f))

    private fun lookup(phrase: Boolean) {
        val wait = Ink.text(this, "…", size); body.addView(wait)
        launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { prefs.library().lookup(query, prefs.order, prefs.disabled) }
            }
            body.removeView(wait)
            val hits = res.getOrElse { note("Dictionary error: ${it.message}"); return@launch }
            if (hits.isEmpty()) note(if (phrase) "Not in dictionaries." else "Not found in dictionaries.")
            hits.forEach { (dict, list) ->
                body.addView(Ink.text(this@CardActivity, dict.name, size * 0.7f, bold = true),
                    Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 10) })
                list.forEach { h ->
                    val head = if (h.word.equals(query, true)) "" else "<b>${h.word}</b><br>"
                    body.addView(Ink.text(this@CardActivity, head + h.html, size, html = true))
                }
            }
        }
    }

    private fun ask(a: Action) {
        val p = prefs.provider
        val block = aiBlock ?: Ink.text(this, "", size).also {
            aiBlock = it; body.addView(it, 0, Ink.lp().apply { bottomMargin = Ink.dp(this@CardActivity, 10) })
        }
        Ai.cached(a, query)?.let { block.text = "${a.name}\n$it"; return }
        block.text = "${a.name}…"
        launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { Ai.ask(p, prefs.effectiveKey(p), prefs.model(p), a, query, prefs.answerLang) }
            }
            block.text = r.fold({ "${a.name}\n$it" }, { "${a.name}: ${it.message ?: it.javaClass.simpleName}" })
        }
    }
}
