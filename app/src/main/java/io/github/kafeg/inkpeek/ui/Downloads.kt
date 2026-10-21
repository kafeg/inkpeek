package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.packs.Component
import io.github.kafeg.inkpeek.stardict.CatalogItem
import io.github.kafeg.inkpeek.stardict.Importer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream

/**
 * One download at a time for the whole process — a pack or another dictionary: it outlives the screen that started it,
 * and whichever screen is open shows its [status] through [listener]; a second one cannot start meanwhile.
 */
object Downloads {
    @Volatile private var job: Job? = null
    private val main = Handler(Looper.getMainLooper())
    private val http = io.github.kafeg.inkpeek.Net.http
    @Volatile var status = ""; private set
    /** The last run's outcome ("Added: …", "Already there: …", "Failed: …"), shown once by the screen that sees it. */
    @Volatile var result: String? = null
    /** The open screen's refresh; called on the main thread on progress and at the end. */
    var listener: (() -> Unit)? = null

    val busy: Boolean get() = job?.isActive == true
    @Volatile private var stop = false

    /** Stops the running download at its next chunk: the half-downloaded file is removed, nothing is installed. */
    fun cancel() { stop = true }

    private fun run(ctx: Context, work: suspend (Context, (String) -> Unit) -> String) {
        if (busy) return
        // In the app's language (Card → Main), not the system's: the texts go to the screens.
        val app = Ink.localized(ctx.applicationContext); result = null; status = "…"; stop = false
        job = CoroutineScope(Dispatchers.IO).launch {
            var last = ""
            // E-ink: redraw only when the text changes (whole percents).
            val r = runCatching { work(app) { s ->
                if (stop) throw java.util.concurrent.CancellationException()
                if (s != last) { last = s; status = s; main.post { listener?.invoke() } } } }
            Prefs(app).reload(); status = ""
            result = r.getOrElse { if (it is java.util.concurrent.CancellationException) app.getString(R.string.cancelled)
                else app.getString(R.string.failed, it.message ?: it.javaClass.simpleName) }
            main.post { listener?.invoke() }
        }
        main.post { listener?.invoke() }
    }

    /** Installs and removes pack parts. */
    fun packs(ctx: Context, install: List<Component>, remove: List<Component>, done: String) = run(ctx) { app, progress ->
        val packs = Prefs(app).packs
        remove.forEach { packs.remove(it) }
        // Models loaded in memory would go on translating with the old files.
        if ((install + remove).any { it.kind == "model" }) io.github.kafeg.inkpeek.mt.Engine.release()
        // The dictionary last: a pair counts as installed by it, so a cancelled or broken download leaves no half pair.
        val added = ArrayList<Component>()
        try {
            install.sortedBy { it.kind == "dict" }.forEachIndexed { i, c ->
                packs.install(c) { got, all -> progress(app.getString(R.string.lang_working, i + 1, install.size, c.id, (got * 100 / all.coerceAtLeast(1)).toInt())) }
                added += c
            }
        } catch (e: Throwable) {
            // Stopped or failed: the parts it brought that no installed pair needs go again (nothing would show them).
            val keep = io.github.kafeg.inkpeek.packs.Packs.needed(install, packs.installedPairs()).map { it.id }.toSet()
            added.filter { it.id !in keep }.forEach { runCatching { packs.remove(it) } }
            throw e
        }
        done
    }

    /** Downloads a newer InkPeek and hands it to Android's installer. */
    fun app(ctx: Context, r: Updates.Release) = run(ctx) { app, progress ->
        val apk = java.io.File(app.cacheDir, "update.apk")
        try {
            http.newCall(Request.Builder().url(r.apk).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val all = resp.body!!.contentLength().takeIf { it > 0 } ?: r.size
                resp.body!!.byteStream().use { inp -> apk.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16); var done = 0L
                    while (true) { val n = inp.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n
                        progress(app.getString(R.string.downloading_pct, "InkPeek ${r.name}", if (all > 0) (done * 100 / all).toInt() else 0)) }
                } }
            }
            Updates.install(app, apk)
        } finally { apk.delete() }
        app.getString(R.string.update_confirm)
    }

    /** Downloads another dictionary into the dictionaries' folder; one already there is not added again. */
    fun dict(ctx: Context, item: CatalogItem, title: String) = run(ctx) { app, progress ->
        val prefs = Prefs(app)
        val got = http.newCall(Request.Builder().url(item.url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body!!; val all = body.contentLength().takeIf { it > 0 } ?: item.size
            val counted = object : FilterInputStream(body.byteStream()) {
                var done = 0L
                fun tick(n: Int) { if (n > 0) { done += n; progress(app.getString(R.string.downloading_pct, title, if (all > 0) (done * 100 / all).toInt() else 0)) } }
                override fun read(): Int = super.read().also { if (it >= 0) tick(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { tick(it) }
            }
            Importer.importStream(item.url.substringAfterLast('/'), counted as InputStream, prefs.dictsDir, app.cacheDir)
        }
        // The address with the dictionaries it brought: the page marks it while one of them is in the folder.
        prefs.got = prefs.got.filterNot { it.substringBefore('\t') == item.url }.toSet() + (listOf(item.url) + got.added.map { it.name } + got.already).joinToString("\t")
        listOfNotNull(got.added.takeIf { it.isNotEmpty() }?.let { app.getString(R.string.added, it.joinToString { d -> d.name }) },
            got.already.takeIf { it.isNotEmpty() }?.let { app.getString(R.string.already_there, it.joinToString()) }).joinToString("\n")
    }
}
