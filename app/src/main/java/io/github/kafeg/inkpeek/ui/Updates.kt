package io.github.kafeg.inkpeek.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import org.json.JSONObject
import java.io.File

/** The app's own releases on GitHub: the latest one, and installing it through Android's installer. */
object Updates {
    private const val LATEST = "https://api.github.com/repos/kafeg/inkpeek/releases/latest"
    const val DAY = 24 * 3600 * 1000L
    /** The last try in this process: without a network the daily check does not go out on every settings visit. */
    @Volatile private var tried = 0L
    /** The installer's confirmation that Android did not let start while InkPeek was in the background. */
    @Volatile var pending: Intent? = null

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
    /** This build has the updater (the GitHub one; F-Droid's and the stores' have not: they update the app themselves). */
    val enabled get() = io.github.kafeg.inkpeek.BuildConfig.UPDATES

    fun newer(ctx: Context): Release? = if (!enabled) null else Release.of(Prefs(ctx).updateSeen)?.takeIf { it.code > versionCode(ctx) }

    /** The daily check is on and a day has passed. */
    fun due(ctx: Context) = enabled && Prefs(ctx).let { it.updateCheck && System.currentTimeMillis().let { now -> now - it.updateAt > DAY && now - tried > DAY / 24 } }

    /** Blocking: asks GitHub for the latest release and remembers it. */
    fun check(ctx: Context): Release {
        tried = System.currentTimeMillis()
        val j = JSONObject(io.github.kafeg.inkpeek.Net.text(LATEST, accept = "application/vnd.github+json"))
        val tag = j.getString("tag_name")
        val assets = j.getJSONArray("assets"); val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name").endsWith(".apk") }
        val r = Release(tag, tag.substringAfterLast('.').toIntOrNull() ?: 0, j.optString("body"), apk.getString("browser_download_url"), apk.optLong("size"))
        Prefs(ctx).apply { updateSeen = r.json(); updateAt = System.currentTimeMillis() }
        return r
    }

    /** The release notes as a few plain lines: "- Copy…" → "— Copy…". */
    fun brief(notes: String) = notes.lines().fold(mutableListOf<String>()) { items, l ->
        // A bullet goes on over the indented lines under it.
        if (l.startsWith("- ")) items += l.removePrefix("- ").trim() else if (l.startsWith("  ") && items.isNotEmpty()) items[items.size - 1] += " " + l.trim()
        items
    }.take(6).joinToString("\n") { "— " + it.replace(Regex("[*`]"), "") }

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
            PackageInstaller.STATUS_PENDING_USER_ACTION -> @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // In the background Android would drop it quietly: the settings show it when they open next.
                val shown = android.app.ActivityManager.RunningAppProcessInfo().also { p -> android.app.ActivityManager.getMyMemoryState(p) }
                    .importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                if (shown) ctx.startActivity(it) else Updates.pending = it
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(ctx, Ink.localized(ctx).getString(R.string.install_failed, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()), Toast.LENGTH_LONG).show()
        }
    }
}
