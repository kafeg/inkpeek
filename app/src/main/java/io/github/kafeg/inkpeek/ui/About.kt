package io.github.kafeg.inkpeek.ui

import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** About: what InkPeek is, the version and the update state, Check now / Update, the daily check, GitHub, licenses. */
object About {
    private const val GITHUB = "https://github.com/kafeg/inkpeek"

    fun show(a: MainActivity) {
        val box = Ink.vertical(a)
        val state = Ink.text(a, "", 15f)
        val action = Ink.button(a, "", Rows.BUTTON) {}
        var dlg: InkDialog? = null
        fun fill() {
            val newer = Updates.newer(a)
            val at = a.prefs.updateAt
            state.text = Ink.arrows(a, when {
                newer != null -> a.getString(R.string.update_new, newer.name) + Updates.brief(newer.notes).let { if (it.isEmpty()) "" else "\n$it" }
                at > 0 -> a.getString(R.string.update_latest, android.text.format.DateFormat.getTimeFormat(a).format(java.util.Date(at)) +
                    if (System.currentTimeMillis() - at > Updates.DAY) " " + android.text.format.DateFormat.getDateFormat(a).format(java.util.Date(at)) else "")
                else -> a.getString(R.string.update_never)
            }, state.textSize)
            action.text = newer?.let { a.getString(R.string.update_btn, (it.size / 1_000_000).toInt().coerceAtLeast(1)) } ?: a.getString(R.string.check_now)
            action.setOnClickListener {
                val n = Updates.newer(a)
                if (n != null) { dlg?.dismiss(); Downloads.app(a, n); a.showDownload(); return@setOnClickListener }
                state.text = a.getString(R.string.checking)
                a.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Updates.check(a) } }
                    r.exceptionOrNull()?.let { state.text = a.getString(R.string.failed, it.message) } ?: fill()
                    a.refreshStatus()
                }
            }
        }
        box.addView(Ink.text(a, "InkPeek " + Updates.versionName(a), 20f, bold = true))
        box.addView(Ink.text(a, a.getString(R.string.about_text), 15f), Ink.lp().apply { topMargin = Ink.dp(a, 6) })
        if (Updates.enabled) {
            box.addView(state, Ink.lp().apply { topMargin = Ink.dp(a, 14) })
            box.addView(action, Ink.lp().apply { topMargin = Ink.dp(a, 8) })
            box.addView(Rows.check(a, a.getString(R.string.check_daily_t), a.getString(R.string.check_daily_d), a.prefs.updateCheck) { a.prefs.updateCheck = it })
        }
        box.addView(Ink.text(a, a.getString(R.string.licenses), 13.5f), Ink.lp().apply { topMargin = Ink.dp(a, 14) })
        if (Updates.enabled) fill()
        dlg = InkDialog(a).view(box)
            .button("GitHub") { runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB))) } }
            .button(R.string.close).show()
    }
}
