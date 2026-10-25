package io.github.kafeg.inkpeek.ui

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import io.github.kafeg.inkpeek.R

/**
 * Copy, share, search the web — a small menu of our icons over what is held, like FBReader's: a whole line (a long
 * press on a list row) or the selection in a long text. It floats over the page: nothing under it moves.
 */
object TextActions {
    /** The selection being shown, so a tap beside it ends it ([outside]). */
    private var active: Pair<TextView, ActionMode>? = null

    /** A tap on an empty place of the screen ends the selection and its menu (a tap on text did already). */
    fun outside(ev: android.view.MotionEvent) {
        val (tv, mode) = active ?: return
        if (ev.actionMasked != android.view.MotionEvent.ACTION_DOWN) return
        // Both in the window's coordinates (the visible part, from its root): the card's window is not at 0, 0 of the screen.
        val r = Rect(); tv.getGlobalVisibleRect(r)
        if (!r.contains(ev.x.toInt(), ev.y.toInt())) mode.finish()
    }

    /** The screen goes (paused, finished): its selection menu with it. */
    fun end() { active?.second?.finish() }

    /** A selection is shown (its handles are being dragged, not the page). */
    val selecting get() = active != null

    /**
     * A text you can select: the handles, our menu instead of Android's, moving with the handles. Double tap: a word;
     * triple tap: the paragraph unless [paragraph] is false (a one-line header: an e-ink double tap often comes as three);
     * a list row is selected the same way (its arrow opens it).
     */
    fun selectable(tv: TextView, paragraph: Boolean = true) {
        tv.setTextIsSelectable(true)
        var taps = 0; var lastUp = 0L; var focused = true; var mine = false
        tv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    taps = if (ev.eventTime - lastUp < 350) taps + 1 else 1; focused = tv.isFocused
                    // The third tap selects the paragraph; Android would take it for a tap that ends the selection. The whole
                    // gesture is ours, and the count starts again (a fourth tap is a first one).
                    mine = paragraph && taps == 3
                    if (mine) { taps = 0; paragraph(tv, tv.getOffsetForPosition(ev.x, ev.y)) }
                    mine
                }
                android.view.MotionEvent.ACTION_UP -> {
                    lastUp = ev.eventTime
                    // Selectable text takes the first tap for the focus, not as a click: a row would open on the second.
                    if (!focused && taps == 1 && tv.hasOnClickListeners() && ev.eventTime - ev.downTime < android.view.ViewConfiguration.getLongPressTimeout()
                        && tv.selectionStart == tv.selectionEnd) tv.post { tv.performClick() }
                    mine
                }
                else -> mine
            }
        }
        tv.customSelectionActionModeCallback = object : ActionMode.Callback {
            // One menu per selection: Android may start a new one before it ends the old (a triple tap), and each menu
            // must go with its own.
            val pops = HashMap<ActionMode, PopupWindow>()
            // Modes not yet ended: a menu posted for a mode that ended meanwhile is not shown (nothing would close it).
            val live = HashSet<ActionMode>()
            // Android's own menu stays empty (so it is not shown, and it reports nothing): the selection is watched here.
            val watch = object : android.text.SpanWatcher {
                override fun onSpanAdded(t: android.text.Spannable, what: Any, a: Int, b: Int) = moved(what)
                override fun onSpanRemoved(t: android.text.Spannable, what: Any, a: Int, b: Int) {}
                override fun onSpanChanged(t: android.text.Spannable, what: Any, a: Int, b: Int, c: Int, d: Int) = moved(what)
            }
            fun moved(what: Any) { if (what === android.text.Selection.SELECTION_START || what === android.text.Selection.SELECTION_END)
                tv.post { val r = selection(tv) ?: return@post; pops.values.forEach { place(it, tv, r, false) } } }
            fun selected() = tv.text.subSequence(tv.selectionStart.coerceAtLeast(0), tv.selectionEnd.coerceAtLeast(0)).toString()
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear(); active?.second?.takeIf { it !== mode }?.finish(); active = tv to mode; live += mode
                val text = tv.text as? android.text.Spannable
                text?.setSpan(watch, 0, text.length, android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                tv.post { if (mode !in live || mode in pops) return@post; val r = selection(tv) ?: return@post
                    pops[mode] = show(tv, r, select = { selected() }, all = { text?.let { android.text.Selection.selectAll(it) } },
                        done = { mode.finish() }) }
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) {
                live -= mode; pops.remove(mode)?.dismiss()
                if (pops.isEmpty()) (tv.text as? android.text.Spannable)?.removeSpan(watch)
                if (active?.second === mode) active = null
            }
        }
    }

    /** The paragraph around [at]: from the line break before it to the one after. */
    private fun paragraph(tv: TextView, at: Int) {
        val t = tv.text as? android.text.Spannable ?: return
        val a = t.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (it < 0 || it >= at) 0 else it + 1 }
        val b = t.indexOf('\n', at).let { if (it < 0) t.length else it }
        if (b > a) android.text.Selection.setSelection(t, a, b)
    }

    /** Where the selection of [tv] is, in its window: its first line to its last. */
    private fun selection(tv: TextView): Rect? {
        val l = tv.layout ?: return null
        val s = tv.selectionStart; val e = tv.selectionEnd
        if (s < 0 || e <= s) return null
        val a = l.getLineForOffset(s); val b = l.getLineForOffset(e)
        val left = if (a == b) l.getPrimaryHorizontal(s).toInt() else 0
        val right = if (a == b) l.getPrimaryHorizontal(e).toInt() else l.width
        val dx = tv.totalPaddingLeft - tv.scrollX; val dy = tv.totalPaddingTop - tv.scrollY
        return inWindow(tv, Rect(left + dx, l.getLineTop(a) + dy, right + dx, l.getLineBottom(b) + dy))
    }

    /** The menu over [at] (window coordinates); [select] reads the selection at the moment of the tap, [all] selects all. */
    private fun show(anchor: View, at: Rect, select: () -> String, all: () -> Unit, done: () -> Unit): PopupWindow {
        val ctx = anchor.context
        val box = Ink.horizontal(ctx).apply { background = Ink.border(ctx, 3, 8); val q = Ink.dp(ctx, 5); setPadding(q, q, q, q) }
        // Over a selection it must not take the focus: the selection would end.
        val pop = PopupWindow(box, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, false)
        fun item(icon: Int, label: Int, act: (String) -> Unit) = box.addView(Ink.icon(ctx, icon, ctx.getString(label), 44) {
            act(select()); pop.dismiss(); done()
        }, LinearLayout.LayoutParams(Ink.dp(ctx, 44), Ink.dp(ctx, 44)).apply { if (box.childCount > 0) leftMargin = Ink.dp(ctx, 6) })
        // The selection in InkPeek itself: in the card ← returns to the word before.
        item(R.drawable.ic_dictionary, R.string.look_up) { s -> s.trim().take(4000).takeIf { it.isNotEmpty() }?.let {
            (ctx as? CardActivity)?.lookUp(it) ?: ctx.startActivity(WordsList.card(ctx, it)) } }
        item(R.drawable.ic_copy, R.string.copy) { copy(ctx, it) }
        item(R.drawable.ic_share, R.string.share) { share(ctx, it) }
        item(R.drawable.ic_web, R.string.search_web) { web(ctx, it) }
        box.addView(Ink.icon(ctx, R.drawable.ic_select_all, ctx.getString(R.string.select_all), 44) { all() },
            LinearLayout.LayoutParams(Ink.dp(ctx, 44), Ink.dp(ctx, 44)).apply { leftMargin = Ink.dp(ctx, 6) })
        pop.elevation = 0f
        place(pop, anchor, at, true)
        return pop
    }

    /** [r] inside [v] in its window's coordinates: what a popup over that window is placed by (the card's is not at 0, 0). */
    private fun inWindow(v: View, r: Rect): Rect { val at = IntArray(2); v.getLocationInWindow(at); return Rect(r).apply { offset(at[0], at[1]) } }

    /** Over [at], or under it when there is no room above; inside the window. */
    private fun place(pop: PopupWindow, anchor: View, at: Rect, first: Boolean) {
        val box = pop.contentView; val ctx = anchor.context
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val gap = Ink.dp(ctx, 6)
        val y = if (at.top - box.measuredHeight - gap >= 0) at.top - box.measuredHeight - gap else at.bottom + gap
        val x = (at.left + (at.width() - box.measuredWidth) / 2).coerceIn(gap, (anchor.rootView.width - box.measuredWidth - gap).coerceAtLeast(gap))
        if (first) pop.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y) else pop.update(x, y, -1, -1)
    }

    private fun copy(ctx: Context, s: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("InkPeek", s))
        Ink.toast(ctx, ctx.getString(R.string.copied), long = false)
    }

    private fun share(ctx: Context, s: String) = runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s), null))
    }

    /** The system's web search; without one, the browser on a search page. */
    private fun web(ctx: Context, s: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, s)) }
        .recoverCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(s)))) }
        .onFailure { Ink.toast(ctx, ctx.getString(R.string.failed, it.message)) }
}
