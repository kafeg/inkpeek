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
import android.widget.Toast
import io.github.kafeg.inkpeek.R

/**
 * Copy, share, search the web — a small menu of our icons over what is held, like FBReader's: a whole line (a long
 * press on a list row) or the selection in a long text. It floats over the page: nothing under it moves.
 */
object TextActions {
    /**
     * A row made of several fields (Notes, history): a long press greys it as a selection is greyed and opens the menu
     * for its whole [text].
     */
    fun line(row: View, text: () -> String) = row.setOnLongClickListener { v ->
        val was = v.background
        v.setBackgroundColor(0x40808080)
        show(v, inWindow(v, Rect(0, 0, v.width, v.height)), text(), done = { v.background = was }); true
    }

    /**
     * A long text: selection with its handles, our menu instead of Android's, moving with the handles. [whole]: a list
     * row — a long press selects all of it, so what will be copied is seen, and the handles can narrow it.
     */
    fun selectable(tv: TextView, whole: Boolean = false) {
        tv.setTextIsSelectable(true)
        tv.customSelectionActionModeCallback = object : ActionMode.Callback {
            var pop: PopupWindow? = null
            // Android's own menu stays empty (so it is not shown, and it reports nothing): the selection is watched here.
            val watch = object : android.text.SpanWatcher {
                override fun onSpanAdded(t: android.text.Spannable, what: Any, a: Int, b: Int) = moved(what)
                override fun onSpanRemoved(t: android.text.Spannable, what: Any, a: Int, b: Int) {}
                override fun onSpanChanged(t: android.text.Spannable, what: Any, a: Int, b: Int, c: Int, d: Int) = moved(what)
            }
            fun moved(what: Any) { if (what === android.text.Selection.SELECTION_START || what === android.text.Selection.SELECTION_END)
                tv.post { val p = pop; val r = selection(tv); if (p != null && r != null) place(p, tv, r, false) } }
            fun selected() = tv.text.subSequence(tv.selectionStart.coerceAtLeast(0), tv.selectionEnd.coerceAtLeast(0)).toString()
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                (tv.text as? android.text.Spannable)?.setSpan(watch, 0, tv.text.length, android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                if (whole) tv.post { (tv.text as? android.text.Spannable)?.let { android.text.Selection.selectAll(it) } }
                tv.post { val r = selection(tv) ?: return@post
                    pop = show(tv, r, null, select = { selected() }, all = { (tv.text as? android.text.Spannable)?.let { android.text.Selection.selectAll(it) } },
                        done = { mode.finish() }) }
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) {
                (tv.text as? android.text.Spannable)?.removeSpan(watch); pop?.dismiss(); pop = null
            }
        }
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

    /**
     * The menu over [at] (window coordinates; under it when there is no room above). [text] is the whole line; for a
     * selection, [select] reads it at the moment of the tap and [all] selects all.
     */
    private fun show(anchor: View, at: Rect, text: String?, select: (() -> String)? = null, all: (() -> Unit)? = null,
                     done: () -> Unit = {}): PopupWindow {
        val ctx = anchor.context
        val box = Ink.horizontal(ctx).apply { background = Ink.border(ctx, 3, 8); val q = Ink.dp(ctx, 5); setPadding(q, q, q, q) }
        // Over a selection it must not take the focus: the selection would end.
        val pop = PopupWindow(box, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, select == null)
        fun item(icon: Int, label: Int, act: (String) -> Unit) = box.addView(Ink.icon(ctx, icon, ctx.getString(label), 44) {
            act(select?.invoke() ?: text.orEmpty()); pop.dismiss(); done()
        }, LinearLayout.LayoutParams(Ink.dp(ctx, 44), Ink.dp(ctx, 44)).apply { if (box.childCount > 0) leftMargin = Ink.dp(ctx, 6) })
        item(R.drawable.ic_copy, R.string.copy) { copy(ctx, it) }
        item(R.drawable.ic_share, R.string.share) { share(ctx, it) }
        item(R.drawable.ic_web, R.string.search_web) { web(ctx, it) }
        if (all != null) box.addView(Ink.icon(ctx, R.drawable.ic_select_all, ctx.getString(R.string.select_all), 44) { all() },
            LinearLayout.LayoutParams(Ink.dp(ctx, 44), Ink.dp(ctx, 44)).apply { leftMargin = Ink.dp(ctx, 6) })
        pop.isOutsideTouchable = select == null; pop.elevation = 0f
        if (select == null) pop.setOnDismissListener { done() }
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
        Toast.makeText(ctx, ctx.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    private fun share(ctx: Context, s: String) = runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s), null))
    }

    /** The system's web search; without one, the browser on a search page. */
    private fun web(ctx: Context, s: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, s)) }
        .recoverCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(s)))) }
        .onFailure { Toast.makeText(ctx, ctx.getString(R.string.failed, it.message), Toast.LENGTH_LONG).show() }
}
