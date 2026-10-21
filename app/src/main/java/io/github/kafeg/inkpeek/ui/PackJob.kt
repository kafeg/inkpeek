package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.packs.Component
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One pack download or removal at a time for the whole process: it outlives the screen that started it (a language
 * change recreates the settings), and whichever screen is open shows its [status] through [listener].
 */
object PackJob {
    private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile var status = ""; private set
    /** The open screen's refresh; called on the main thread on progress and at the end. */
    var listener: (() -> Unit)? = null
    /** The last run's error, shown once by the screen that sees it. */
    var error: Throwable? = null

    val busy: Boolean get() = job?.isActive == true

    fun run(ctx: Context, install: List<Component>, remove: List<Component>) {
        if (busy) return
        val app = ctx.applicationContext; val packs = Prefs(app).packs; val res = app.resources
        error = null
        job = CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                remove.forEach { packs.remove(it) }
                install.forEachIndexed { i, c ->
                    var shown = -1
                    packs.install(c) { done, all ->
                        val pct = (done * 100 / all.coerceAtLeast(1)).toInt()
                        // E-ink: redraw only when the percentage changes.
                        if (pct != shown) { shown = pct; status = res.getString(R.string.lang_working, i + 1, install.size, c.id, pct); main.post { listener?.invoke() } }
                    }
                }
            }.onFailure { error = it }
            Prefs(app).reload(); status = ""
            main.post { job = null; listener?.invoke() }
        }
        main.post { listener?.invoke() }
    }
}
