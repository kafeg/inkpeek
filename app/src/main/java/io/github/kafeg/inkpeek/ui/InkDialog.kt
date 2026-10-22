package io.github.kafeg.inkpeek.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * A dialog drawn by us, in the app's theme: Boox paints Android's own dialogs white whatever the theme. A title, the
 * content (scrolls when long), the buttons in one row in the order given.
 */
class InkDialog(private val ctx: Context) {
    private var title: String? = null
    private var content: View? = null
    private var scroll = true
    private var cancelable = true
    private val buttons = ArrayList<Triple<String, Boolean, (InkDialog) -> Unit>>()
    private var dialog: Dialog? = null
    /** The buttons as shown, in order (a button can change itself: "Cancel" turns off while the download stops). */
    val views = ArrayList<TextView>()

    fun title(t: String) = apply { title = t }
    fun title(id: Int) = apply { title = ctx.getString(id) }
    fun message(s: CharSequence) = apply { content = Ink.text(ctx, s, 17f) }
    /** [scroll] false: the content scrolls by itself (a list). */
    fun view(v: View, scroll: Boolean = true) = apply { content = v; this.scroll = scroll }
    fun cancelable(b: Boolean) = apply { cancelable = b }
    /** A button; it closes the dialog after [act] unless [keep]. */
    fun button(label: String, keep: Boolean = false, act: (InkDialog) -> Unit = {}) = apply { buttons += Triple(label, keep, act) }
    fun button(id: Int, keep: Boolean = false, act: (InkDialog) -> Unit = {}) = button(ctx.getString(id), keep, act)

    val isShowing: Boolean get() = dialog?.isShowing == true
    fun dismiss() { runCatching { dialog?.dismiss() }; dialog = null }

    fun show(): InkDialog = apply {
        val q = Ink.dp(ctx, 16)
        val col = Ink.vertical(ctx).apply { background = Ink.border(ctx, 3, 8); setPadding(q, q, q, q) }
        title?.let { col.addView(Ink.text(ctx, it, 19f, bold = true), Ink.lp().apply { bottomMargin = Ink.dp(ctx, 10) }) }
        // Weighted: a long content shrinks to the screen and scrolls, the buttons stay under it.
        content?.let { c -> col.addView(if (scroll) ScrollView(ctx).apply { addView(c) } else c, Ink.lp(weight = 1f)) }
        if (buttons.isNotEmpty()) col.addView(Ink.horizontal(ctx).apply {
            isBaselineAligned = false
            buttons.forEachIndexed { i, (label, keep, act) ->
                val b = Ink.button(ctx, label, Rows.BUTTON) { act(this@InkDialog); if (!keep) dismiss() }
                views += b
                addView(b, Ink.lp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = Ink.dp(ctx, 8) })
            }
        }, Ink.lp().apply { topMargin = Ink.dp(ctx, 14) })
        dialog = Dialog(ctx).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(col)
            setCancelable(cancelable); setCanceledOnTouchOutside(cancelable)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.setWindowAnimations(0)
            // As wide as Android's dialogs on a phone, a little narrower on a big e-ink screen.
            val w = ctx.resources.displayMetrics.widthPixels
            window?.setLayout((w * if (w > Ink.dp(ctx, 600)) 0.8 else 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            show()
        }
    }
}
