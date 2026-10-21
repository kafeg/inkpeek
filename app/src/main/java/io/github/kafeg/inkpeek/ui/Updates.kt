package io.github.kafeg.inkpeek.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's own releases on GitHub: the latest one, and installing it through Android's installer. */
object Updates {
    private const val LATEST = "https://api.github.com/repos/kafeg/inkpeek/releases/latest"
    private const val DAY = 24 * 3600 * 1000L
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    /** A release: [code] is the tag's last number (the commit count, also the version code), [notes] its text. */
    data class Release(val tag: String, val code: Int, val notes: String, val apk: String, val size: Long) {
        val name: String get() = tag.removePrefix("v")
        fun json() = JSONObject().put("tag", tag).put("code", code).put("notes", notes).put("apk", apk).put("size", size).toString()
        companion object {
            fun of(json: String) = runCatching { JSONObject(json).let { Release(it.getString("tag"), it.getInt("code"), it.optString("notes"), it.getString("apk"), it.optLong("size")) } }.getOrNull()
        }
    }

    fun versionName(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
    private fun versionCode(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    /** The latest release seen by the last check, when it is newer than this app. */
    fun newer(ctx: Context): Release? = Release.of(Prefs(ctx).updateSeen)?.takeIf { it.code > versionCode(ctx) }

    /** The daily check is on and a day has passed. */
    fun due(ctx: Context) = Prefs(ctx).let { it.updateCheck && System.currentTimeMillis() - it.updateAt > DAY }

    /** Blocking: asks GitHub for the latest release and remembers it. */
    fun check(ctx: Context): Release {
        val j = http.newCall(Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}"); JSONObject(it.body!!.string())
        }
        val tag = j.getString("tag_name")
        val assets = j.getJSONArray("assets"); val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name").endsWith(".apk") }
        val r = Release(tag, tag.substringAfterLast('.').toIntOrNull() ?: 0, j.optString("body"), apk.getString("browser_download_url"), apk.optLong("size"))
        Prefs(ctx).apply { updateSeen = r.json(); updateAt = System.currentTimeMillis() }
        return r
    }

    /** The release notes as a few plain lines: "- Copy…" → "— Copy…". */
    fun brief(notes: String, lines: Int = 6) = notes.lines().fold(mutableListOf<String>()) { items, l ->
        // A bullet goes on over the indented lines under it.
        if (l.startsWith("- ")) items += l.removePrefix("- ").trim() else if (l.startsWith("  ") && items.isNotEmpty()) items[items.size - 1] += " " + l.trim()
        items
    }.take(lines).joinToString("\n") { "— " + it.replace(Regex("[*`]"), "") }

    /** Hands a downloaded APK to Android's installer; it asks the person to confirm (and once to allow InkPeek). */
    fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        pi.openSession(id).use { s ->
            s.openWrite("inkpeek.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val back = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            s.commit(back.intentSender)
        }
    }
}

/** The installer's answer: show its confirmation screen, or say why it failed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(ctx, Ink.localized(ctx).getString(R.string.install_failed, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()), Toast.LENGTH_LONG).show()
        }
    }
}
