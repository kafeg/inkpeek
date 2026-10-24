package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import io.github.kafeg.inkpeek.R

/**
 * The settings' one row style: a box, an icon or nothing on the left; a title in one line; an explanation of at most
 * two lines. Every section is built of these, so all of them look the same.
 */
object Rows {
    private const val TITLE = 16f
    private const val ABOUT = 13.5f
    const val BUTTON = 17f

    private fun gap(ctx: Context) = Ink.lp().apply { topMargin = Ink.dp(ctx, 12) }

    /**
     * Title (one line) and explanation (at most [lines], cut with "…"): two for a setting, one for a list item's state,
     * more for help.
     */
    fun texts(ctx: Context, title: String, about: String, lines: Int = 2) = Ink.vertical(ctx).apply {
        addView(Ink.text(ctx, title, TITLE, bold = true).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        if (about.isNotEmpty()) addView(Ink.text(ctx, about, ABOUT).apply { maxLines = lines; ellipsize = TextUtils.TruncateAt.END },
            Ink.lp().apply { topMargin = Ink.dp(ctx, 1) })
    }

    /** A row that depends on another setting: dimmed and untouchable while that one is off. */
    fun enable(v: View, on: Boolean) {
        v.alpha = if (on) 1f else 0.4f
        fun walk(x: View) { x.isEnabled = on; (x as? ViewGroup)?.let { g -> (0 until g.childCount).forEach { walk(g.getChildAt(it)) } } }
        walk(v)
    }

    /**
     * A setting that is on or off; a tap on the text toggles it too. [remove]: a ✕ on the right (it asks first) — a list
     * item (a pack, a dictionary), whose second line is its state.
     */
    fun check(ctx: Context, title: String, about: String, on: Boolean, remove: (() -> Unit)? = null, save: (Boolean) -> Unit) =
        Ink.horizontal(ctx).apply {
            isBaselineAligned = false
            val box = CheckBox(ctx).apply { isChecked = on; setOnCheckedChangeListener { _, v -> save(v) } }
            addView(box, Ink.lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ink.dp(ctx, 6) })
            addView(texts(ctx, title, about, if (remove != null) 1 else 2).apply { setOnClickListener { if (box.isEnabled) box.toggle() } }, Ink.lp(0, weight = 1f))
            if (remove != null) addView(Ink.button(ctx, "✕", 16f) { remove() },
                Ink.lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ink.dp(ctx, 8); gravity = Gravity.CENTER_VERTICAL })
            layoutParams = gap(ctx)
        }

    /** A setting with a choice: its title and explanation, the drop-down under them. */
    fun choice(ctx: Context, title: String, about: String, items: List<String>, selected: Int, pick: (Int) -> Unit) = Ink.vertical(ctx).apply {
        addView(texts(ctx, title, about))
        addView(spinner(ctx, items, selected, pick), Ink.lp().apply { topMargin = Ink.dp(ctx, 4) })
        layoutParams = gap(ctx)
    }

    /**
     * A help row: an icon (or none), the title, [lines] of text; [open] makes the row a link with a ✎ on the right, or
     * a › when it opens a page ([page]).
     */
    fun info(ctx: Context, icon: Int?, title: String, about: String, lines: Int = 2, page: Boolean = false, open: (() -> Unit)? = null) = Ink.horizontal(ctx).apply {
        isBaselineAligned = false
        if (icon != null) addView(ImageView(ctx).apply { setImageResource(icon); setColorFilter(Ink.fg(ctx)) },
            LinearLayout.LayoutParams(Ink.dp(ctx, 28), Ink.dp(ctx, 28)).apply { rightMargin = Ink.dp(ctx, 12); topMargin = Ink.dp(ctx, 2) })
        addView(texts(ctx, title, about, lines), Ink.lp(0, weight = 1f))
        if (open != null) {
            isClickable = true; setOnClickListener { open() }
            if (page) addView(Ink.icon(ctx, R.drawable.ic_next, title, 44) { open() },
                LinearLayout.LayoutParams(Ink.dp(ctx, 44), Ink.dp(ctx, 44)).apply { leftMargin = Ink.dp(ctx, 8); gravity = Gravity.CENTER_VERTICAL })
            else addView(Ink.button(ctx, "✎", 16f) { open() }, Ink.lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ink.dp(ctx, 8); gravity = Gravity.CENTER_VERTICAL })
        }
        layoutParams = gap(ctx)
    }

    fun heading(ctx: Context, title: String, first: Boolean = false) = Ink.text(ctx, title, 18f, bold = true).apply {
        layoutParams = Ink.lp().apply { topMargin = Ink.dp(ctx, if (first) 0 else 22) }
    }

    /** A plain line (an empty list, a status). */
    fun note(ctx: Context, s: String) = Ink.text(ctx, s, ABOUT).apply { layoutParams = gap(ctx) }

    /** Buttons side by side, all as tall as the tallest (a narrow screen wraps a label). */
    fun buttons(ctx: Context, vararg items: Pair<String, () -> Unit>) = Ink.horizontal(ctx).apply {
        isBaselineAligned = false
        items.forEachIndexed { i, (label, action) ->
            addView(Ink.button(ctx, label, BUTTON) { action() }, Ink.lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = Ink.dp(ctx, 8) })
        }
        layoutParams = gap(ctx)
    }

    /** Every delete asks first. */
    fun confirm(ctx: Context, message: String, yes: Int = R.string.delete, action: () -> Unit) {
        InkDialog(ctx).message(message).button(R.string.cancel).button(yes) { action() }.show()
    }

    /** Up to three sub-tabs over [pages]; [open] remembers the choice. Returns what shows one (Help's "Open in settings"). */
    fun subTabs(ctx: Context, box: LinearLayout, pages: List<Triple<String, String, View>>, open: String, picked: (String) -> Unit): (String) -> Unit {
        val bar = Ink.vertical(ctx); box.addView(bar)
        pages.forEach { box.addView(it.third) }
        fun show(k: String) {
            pages.forEach { (key, _, v) -> v.visibility = if (key == k) View.VISIBLE else View.GONE }
            bar.removeAllViews()
            bar.addView(Ink.subTabs(ctx, pages.map { it.first to it.second }, k, 16f) { show(it); picked(it) },
                Ink.lp().apply { bottomMargin = Ink.dp(ctx, 6) })
        }
        show(pages.firstOrNull { it.first == open }?.first ?: pages.first().first)
        return { k -> show(k); picked(k) }
    }

    /** A long list (the download pages) of rows in the same style: a title and one line under it. */
    fun adapter(ctx: Context, items: List<Pair<String, String>>) = object : android.widget.BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(i: Int) = items[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, old: View?, parent: ViewGroup?): View =
            texts(ctx, items[i].first, items[i].second, 1).apply { val q = Ink.dp(ctx, 10); setPadding(0, q, 0, q) }
    }

    /** A page's header: ← (back to the settings; e-ink readers often have no back gesture) and its title. */
    fun header(a: android.app.Activity, title: String) = Ink.horizontal(a).apply {
        gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
        addView(Ink.icon(a, R.drawable.ic_back, a.getString(R.string.back)) { a.finish() },
            LinearLayout.LayoutParams(Ink.dp(a, 48), Ink.dp(a, 48)).apply { rightMargin = Ink.dp(a, 12) })
        addView(Ink.text(a, title, 22f, bold = true).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }, Ink.lp(0, weight = 1f))
    }

    /** The running download in a dialog: its progress, then its outcome; closing it leaves the download running. */
    class JobDialog(private val a: android.app.Activity) {
        private var d: InkDialog? = null
        private var text: android.widget.TextView? = null

        /** Cancel stops the download; Hide closes the dialog and leaves it running; at the end only Close stays. */
        fun show() {
            if (d?.isShowing == true) return
            text = Ink.text(a, Downloads.status, 17f)
            // Cancel keeps the dialog: it shows "Cancelled" when the download has stopped.
            d = InkDialog(a).view(text!!).cancelable(false)
                .button(R.string.cancel_download, keep = true) { it.views[0].isEnabled = false; it.views[0].alpha = Ink.OFF; Downloads.cancel() }
                .button(R.string.hide).show()
        }

        /** On every progress step; the outcome goes into the open dialog, else into a toast. */
        fun update() {
            if (Downloads.busy) { text?.text = Downloads.status; return }
            val r = Downloads.result ?: return; Downloads.result = null
            // The outcome in a dialog of its own with one button.
            if (d?.isShowing == true) { d?.dismiss(); d = InkDialog(a).message(r).button(R.string.close).show() }
            else Ink.toast(a, r)
        }

        /** The screen goes: its dialogs with it (a dialog over a finished screen leaks its window). */
        fun dismiss() { d?.dismiss(); d = null }
    }

    /** A drop-down list in our ink and paper (Android's item box is white on white in the dark theme). */
    fun spinner(ctx: Context, items: List<String>, selected: Int, onPick: (Int) -> Unit) = Spinner(ctx).apply {
        adapter = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_dropdown_item, items) {
            fun styled(v: View, open: Boolean) = v.also { (it as? android.widget.TextView)?.run {
                setTextColor(Ink.fg(ctx)); background = Ink.border(ctx, 2, if (open) 0 else 2) } }
            override fun getView(i: Int, v: View?, p: ViewGroup) = styled(super.getView(i, v, p), false)
            override fun getDropDownView(i: Int, v: View?, p: ViewGroup) = styled(super.getDropDownView(i, v, p), true)
        }
        tag = "spinner"; setPopupBackgroundDrawable(Ink.border(ctx, 3, 2))
        setSelection(selected)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // Only a real change: a recreated screen reports its selection again (the app language recreates the
            // screen, and a repeated report looped it).
            private var current = selected
            override fun onItemSelected(a: AdapterView<*>?, v: View?, i: Int, id: Long) { if (i != current) { current = i; onPick(i) } }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
    }
}
