package io.github.kafeg.inkpeek.packs

import com.github.luben.zstd.ZstdInputStream
import io.github.kafeg.inkpeek.mt.Engine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** One downloadable part of the language packs (see packs/README.md): grammar, dict, model or langid. */
data class Component(val id: String, val kind: String, val from: String, val to: String, val file: String,
                     val size: Long, val sha256: String, val weak: Boolean = false)

/**
 * InkPeek's language packs from this project's release "packs" — the only source the app downloads them from.
 * Layout under [root]: dicts/<id>/ (shown in the card), grammar/<id>/ (header, language), langid.ftz,
 * installed.json (id → sha256); models go to [models]/<from><to>/ as the offline engine expects.
 */
class Packs(val root: File, val models: File) {
    val dicts get() = File(root, "dicts")
    val grammar get() = File(root, "grammar")
    val langid get() = File(root, "langid.ftz")
    private val state get() = File(root, "installed.json")

    fun installed(): Map<String, String> = runCatching {
        JSONObject(state.readText()).let { j -> j.keys().asSequence().associateWith { j.getString(it) } }
    }.getOrDefault(emptyMap())

    private fun mark(id: String, sha: String?) {
        val j = JSONObject(installed() as Map<*, *>)
        if (sha == null) j.remove(id) else j.put(id, sha)
        root.mkdirs(); state.writeText(j.toString(1))
    }

    /** Downloads one component, checks its sha256, unpacks it in place; [progress]: bytes done of the total. */
    fun install(c: Component, progress: (Long, Long) -> Unit = { _, _ -> }) {
        root.mkdirs()
        val tmp = File(root, c.file + ".part")
        http.newCall(Request.Builder().url(ASSETS + c.file).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}: ${c.file}")
            val md = MessageDigest.getInstance("SHA-256")
            r.body!!.byteStream().use { inp -> tmp.outputStream().use { o ->
                val buf = ByteArray(1 shl 16); var done = 0L
                while (true) { val n = inp.read(buf); if (n < 0) break; o.write(buf, 0, n); md.update(buf, 0, n); done += n; progress(done, c.size) }
            } }
            if (md.digest().joinToString("") { "%02x".format(it) } != c.sha256) { tmp.delete(); error("Checksum mismatch: ${c.file}") }
        }
        try {
            when (c.kind) {
                "dict", "grammar" -> unzip(tmp, File(if (c.kind == "dict") dicts else grammar, c.id))
                "model" -> untar(tmp, File(models, c.from + c.to))
                "langid" -> { langid.delete(); tmp.renameTo(langid) }
                else -> error("Unknown pack kind ${c.kind}")
            }
        } finally { tmp.delete() }
        mark(c.id, c.sha256)
    }

    fun remove(c: Component) {
        when (c.kind) {
            "dict" -> File(dicts, c.id).deleteRecursively()
            "grammar" -> File(grammar, c.id).deleteRecursively()
            "model" -> File(models, c.from + c.to).deleteRecursively()
            "langid" -> langid.delete()
        }
        mark(c.id, null)
    }

    /** Book languages that have this answer language: those with a dictionary into it. */
    fun installedBooks(answer: String): List<String> = installed().keys.filter { it.startsWith("dict-") && it.endsWith("-$answer") }
        .map { it.removePrefix("dict-").substringBefore('-') }

    private fun unzip(zip: File, dest: File) {
        val tmp = File(dest.path + ".new"); tmp.deleteRecursively(); tmp.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val out = File(tmp, e.name.substringAfterLast('/')) // one folder per component: keep the file names only
                out.outputStream().use { z.copyTo(it) }
            }
        }
        dest.deleteRecursively(); if (!tmp.renameTo(dest)) error("Cannot move ${dest.name}")
    }

    private fun untar(tarZst: File, dest: File) {
        val tmp = File(dest.path + ".new"); tmp.deleteRecursively(); tmp.mkdirs()
        TarArchiveInputStream(ZstdInputStream(tarZst.inputStream().buffered())).use { t ->
            while (true) {
                val e = t.nextEntry ?: break
                if (e.isDirectory) continue
                File(tmp, e.name.substringAfterLast('/')).outputStream().use { t.copyTo(it) }
            }
        }
        dest.deleteRecursively(); if (!tmp.renameTo(dest)) error("Cannot move ${dest.name}")
    }

    companion object {
        const val ASSETS = "https://github.com/kafeg/inkpeek/releases/download/packs/"
        private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

        fun parse(json: String): List<Component> {
            val a = JSONObject(json).getJSONArray("components")
            return (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
                val f = c.getJSONObject("file")
                Component(c.getString("id"), c.getString("kind"), c.optString("from"), c.optString("to"),
                    f.getString("name"), f.getLong("size"), f.getString("sha256"), c.optBoolean("weak"))
            }
        }

        fun manifest(): List<Component> = http.newCall(Request.Builder().url(ASSETS + "packs.json").build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}"); parse(it.body!!.string())
        }

        /** Answer languages offered: those with a dictionary into them. */
        fun answers(cs: List<Component>) = cs.filter { it.kind == "dict" }.map { it.to }.distinct()

        /** Book languages for an answer language. */
        fun books(cs: List<Component>, answer: String) = cs.filter { it.kind == "dict" && it.to == answer }.map { it.from }.distinct()

        /** Everything the chosen book languages need for [answer]: grammar, dictionary, models (through English), langid. */
        fun needed(cs: List<Component>, books: Collection<String>, answer: String): List<Component> {
            val ids = books.flatMap { x ->
                listOf("grammar-$x", "dict-$x-$answer") + Engine.steps(x, answer).map { (a, b) -> "model-$a-$b" }
            } + if (books.isEmpty()) emptyList() else listOf("langid")
            return cs.filter { it.id in ids.toSet() }
        }
    }
}
