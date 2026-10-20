package io.github.kafeg.inkpeek.mt

import com.github.luben.zstd.ZstdInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** One file of a Firefox Translations model: model, lex (shortlist), vocab or srcvocab / trgvocab. */
data class ModelFile(val name: String, val type: String, val url: String, val size: Long, val sha256: String)

/** A translation direction (always to or from English) at one version and architecture. */
data class ModelSet(val from: String, val to: String, val version: String, val arch: String, val files: List<ModelFile>) {
    val size: Long get() = files.sumOf { it.size }
}

/**
 * Mozilla's model list (Remote Settings `translations-models-v2`, the one Firefox uses) and downloads into
 * `<root>/<from><to>/`. Every pair goes through English: de→ru = de→en + en→ru.
 */
object Models {
    const val RECORDS = "https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/translations-models-v2/records"
    private const val FILES = "https://firefox-settings-attachments.cdn.mozilla.net/"
    /** Best first: the full model, then the smaller ones. */
    private val ARCH = listOf("base", "base-memory", "tiny")
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    /** Complete sets per direction, the best one first; skips nightly-only and alpha (3.0a1) records. */
    fun parse(json: String): Map<Pair<String, String>, List<ModelSet>> {
        val a = JSONObject(json).getJSONArray("data")
        val recs = (0 until a.length()).map { a.getJSONObject(it) }.filter { r ->
            !r.optString("filter_expression").contains("channel") && r.getString("version").all { it.isDigit() || it == '.' }
        }
        return recs.groupBy { Triple(it.getString("sourceLanguage") to it.getString("targetLanguage"), it.getString("version"), it.getString("architecture")) }
            .mapNotNull { (k, rs) ->
                val files = rs.map { r -> val att = r.getJSONObject("attachment")
                    ModelFile(r.getString("name"), r.getString("fileType"), FILES + att.getString("location"),
                        r.optLong("decompressedSize", att.optLong("size")), r.getString("decompressedHash")) }
                val types = files.map { it.type }.toSet()
                val complete = "model" in types && "lex" in types && ("vocab" in types || ("srcvocab" in types && "trgvocab" in types))
                if (complete) ModelSet(k.first.first, k.first.second, k.second, k.third, files.distinctBy { it.type }) else null
            }
            .groupBy { it.from to it.to }
            .mapValues { (_, sets) -> sets.sortedWith(compareBy<ModelSet>({ ARCH.indexOf(it.arch).let { i -> if (i < 0) 99 else i } })
                .thenByDescending { v -> v.version.split('.').map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 0 } * 1000 + it.getOrElse(1) { 0 } } }) }
    }

    fun list(): Map<Pair<String, String>, List<ModelSet>> = parse(get(RECORDS))

    fun dir(root: File, from: String, to: String) = File(root, from + to)

    /** Installed: every file of the set is there (a "set" file names the version and architecture). */
    fun installed(root: File, from: String, to: String): Boolean = File(dir(root, from, to), "set").exists()

    /** Downloads, unpacks (zstd) and checks each file; [progress] gets bytes done of the total. */
    fun download(root: File, set: ModelSet, progress: (Long, Long) -> Unit = { _, _ -> }) {
        val d = dir(root, set.from, set.to); val tmp = File(d.path + ".part")
        tmp.deleteRecursively(); tmp.mkdirs()
        var done = 0L
        set.files.forEach { f ->
            val out = File(tmp, f.name)
            http.newCall(Request.Builder().url(f.url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}: ${f.name}")
                val md = MessageDigest.getInstance("SHA-256")
                ZstdInputStream(r.body!!.byteStream()).use { inp -> out.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    while (true) { val n = inp.read(buf); if (n < 0) break; o.write(buf, 0, n); md.update(buf, 0, n); done += n; progress(done, set.size) }
                } }
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (hex != f.sha256) error("Checksum mismatch: ${f.name}")
            }
        }
        File(tmp, "set").writeText("${set.version} ${set.arch}\n")
        d.deleteRecursively(); if (!tmp.renameTo(d)) error("Cannot move models into ${d.path}")
    }

    private fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) error("HTTP ${it.code}"); it.body!!.string()
    }
}
