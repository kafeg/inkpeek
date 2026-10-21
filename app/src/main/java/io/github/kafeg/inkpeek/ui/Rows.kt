package io.github.kafeg.inkpeek.ui

import android.app.AlertDialog
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

    private fun gap(ctx: Context) = Ink.lp().apply { topMargin = Ink.dp(ctx, 14) }

    /**
     * Title (one line) and explanation, both cut with "…". A setting's or help's explanation always takes two lines, so
     * rows are of one height; a list item's ([item]) is its one status line.
     */
    fun texts(ctx: Context, title: String, about: String, item: Boolean = false) = Ink.vertical(ctx).apply {
        addView(Ink.text(ctx, title, TITLE, bold = true).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        if (about.isNotEmpty()) addView(Ink.text(ctx, about, ABOUT).apply { if (item) isSingleLine = true else { minLines = 2; maxLines = 2 }; ellipsize = TextUtils.TruncateAt.END })
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
            addView(texts(ctx, title, about, item = remove != null).apply { setOnClickListener { box.toggle() } }, Ink.lp(0, weight = 1f))
            if (remove != null) addView(Ink.button(ctx, "✕", 16f) { remove() },
                Ink.lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ink.dp(ctx, 8); gravity = Gravity.CENTER_VERTICAL })
            layoutParams = gap(ctx)
        }

    /** A setting with a choice: its title and explanation, the drop-down under them. */
    fun choice(ctx: Context, title: String, about: String, items: List<String>, selected: Int, pick: (Int) -> Unit) = Ink.vertical(ctx).apply {
        addView(texts(ctx, title, about))
        addView(spinner(ctx, items, selected, pick), Ink.lp().apply { topMargin = Ink.dp(ctx, 6) })
        layoutParams = gap(ctx)
    }

    /** A help row: an icon (or none), the title, two lines; [open] adds a ✎ on the right and makes the row a link. */
    fun info(ctx: Context, icon: Int?, title: String, about: String, open: (() -> Unit)? = null) = Ink.horizontal(ctx).apply {
        isBaselineAligned = false
        if (icon != null) addView(ImageView(ctx).apply { setImageResource(icon) },
            LinearLayout.LayoutParams(Ink.dp(ctx, 28), Ink.dp(ctx, 28)).apply { rightMargin = Ink.dp(ctx, 12); topMargin = Ink.dp(ctx, 2) })
        addView(texts(ctx, title, about), Ink.lp(0, weight = 1f))
        if (open != null) {
            isClickable = true; setOnClickListener { open() }
            addView(Ink.button(ctx, "✎", 16f) { open() }, Ink.lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ink.dp(ctx, 8); gravity = Gravity.CENTER_VERTICAL })
        }
        layoutParams = gap(ctx)
    }

    fun heading(ctx: Context, title: String, first: Boolean = false) = Ink.text(ctx, title, 18f, bold = true).apply {
        layoutParams = Ink.lp().apply { topMargin = Ink.dp(ctx, if (first) 0 else 24) }
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
        AlertDialog.Builder(ctx).setMessage(message).setPositiveButton(yes) { _, _ -> action() }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Three sub-tabs over [pages]; [open] remembers the choice. */
    fun subTabs(ctx: Context, box: LinearLayout, pages: List<Triple<String, String, View>>, open: String, picked: (String) -> Unit) {
        val bar = Ink.vertical(ctx); box.addView(bar)
        pages.forEach { box.addView(it.third) }
        fun show(k: String) {
            pages.forEach { (key, _, v) -> v.visibility = if (key == k) View.VISIBLE else View.GONE }
            bar.removeAllViews()
            bar.addView(Ink.subTabs(ctx, pages.map { it.first to it.second }, k, 16f) { show(it); picked(it) },
                Ink.lp().apply { bottomMargin = Ink.dp(ctx, 6) })
        }
        show(pages.firstOrNull { it.first == open }?.first ?: pages.first().first)
    }

    fun spinner(ctx: Context, items: List<String>, selected: Int, onPick: (Int) -> Unit) = Spinner(ctx).apply {
        adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var first = true
            override fun onItemSelected(a: AdapterView<*>?, v: View?, i: Int, id: Long) { if (first) { first = false; return }; onPick(i) }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
    }
}
