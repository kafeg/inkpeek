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

/** One downloadable part of the language packs (see packs/README.md): dict, model or langid. */
data class Component(val id: String, val kind: String, val from: String, val to: String, val file: String,
                     val size: Long, val sha256: String)

/**
 * InkPeek's language packs from this project's release "packs" — the only source the app downloads them from.
 * Layout under [root]: dicts/<id>/ (the card's dictionaries; the first line of an article is the header), langid.ftz,
 * installed.json (id → sha256); models go to [models]/<from><to>/ as the offline engine expects.
 */
class Packs(val root: File, val models: File) {
    val dicts get() = File(root, "dicts")
    val langid get() = File(root, "langid.ftz")
    private val state get() = File(root, "installed.json")
    private val listFile get() = File(root, "packs.json")

    /** The packs list from the release; the last one received is kept, so installed packs show up offline too. */
    fun list(): List<Component> {
        val json = http.newCall(Request.Builder().url(ASSETS + "packs.json").build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}"); it.body!!.string()
        }
        return parse(json).also { root.mkdirs(); write(listFile, json) }
    }

    fun cachedList(): List<Component>? = runCatching { parse(listFile.readText()) }.getOrNull()

    /** Write to a temporary file, then rename: a crash leaves the old file, never half of one. */
    private fun write(f: File, text: String) {
        val tmp = File(f.path + ".tmp"); tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun installed(): Map<String, String> = runCatching {
        JSONObject(state.readText()).let { j -> j.keys().asSequence().associateWith { j.getString(it) } }
    }.getOrDefault(emptyMap())

    private fun mark(id: String, sha: String?) {
        val j = JSONObject(installed() as Map<*, *>)
        if (sha == null) j.remove(id) else j.put(id, sha)
        root.mkdirs(); write(state, j.toString(1))
    }

    /** Downloads one component, checks its sha256, unpacks it in place; [progress]: bytes done of the total. */
    fun install(c: Component, progress: (Long, Long) -> Unit = { _, _ -> }) {
        root.mkdirs(); File(root, "tmp").deleteRecursively()
        // The download, the unpacked files and the old version side by side at the peak.
        val free = root.usableSpace
        if (free < c.size * 3) error("Not enough free space for ${c.file}: ${c.size * 3 / 1_000_000} MB needed, ${free / 1_000_000} MB free")
        val tmp = File(root, c.file + ".part")
        try { download(c, tmp, progress) } catch (e: Throwable) { tmp.delete(); throw e }
        try {
            when (c.kind) {
                "dict" -> unzip(tmp, File(dicts, c.id))
                "model" -> untar(tmp, File(models, c.from + c.to))
                "langid" -> { langid.delete(); if (!tmp.renameTo(langid)) error("Cannot move ${langid.name}") }
                else -> error("Unknown pack kind ${c.kind}")
            }
        } finally { tmp.delete(); File(root, "tmp").deleteRecursively() }
        mark(c.id, c.sha256)
    }

    private fun download(c: Component, tmp: File, progress: (Long, Long) -> Unit) {
        http.newCall(Request.Builder().url(ASSETS + c.file).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}: ${c.file}")
            val md = MessageDigest.getInstance("SHA-256")
            r.body!!.byteStream().use { inp -> tmp.outputStream().use { o ->
                val buf = ByteArray(1 shl 16); var done = 0L
                while (true) { val n = inp.read(buf); if (n < 0) break; o.write(buf, 0, n); md.update(buf, 0, n); done += n; progress(done, c.size) }
            } }
            if (md.digest().joinToString("") { "%02x".format(it) } != c.sha256) error("Checksum mismatch: ${c.file}")
        }
    }

    fun remove(c: Component) {
        when (c.kind) {
            "dict" -> File(dicts, c.id).deleteRecursively()
            "grammar" -> File(root, "grammar").deleteRecursively() // old builds installed grammar packs
            "model" -> if (c.from.isNotBlank() && c.to.isNotBlank()) File(models, c.from + c.to).deleteRecursively()
            "langid" -> langid.delete()
        }
        mark(c.id, null)
    }

    /** Installed pairs (from → to). */
    fun installedPairs(): List<Pair<String, String>> = installed().keys.filter { it.startsWith("dict-") }
        .map { it.removePrefix("dict-").let { p -> p.substringBefore('-') to p.substringAfter('-') } }

    /** Book languages that have this answer language: those with a dictionary into it. */
    fun installedBooks(answer: String): List<String> = installedPairs().filter { it.second == answer }.map { it.first }

    /** Unpacked outside the library first: the card must not see a half-written dictionary. */
    private fun staging(dest: File) = File(root, "tmp/" + dest.name).also { it.deleteRecursively(); it.mkdirs() }

    private fun unzip(zip: File, dest: File) {
        val tmp = staging(dest)
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
        val tmp = staging(dest)
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

        /** An installed component the packs list no longer has, rebuilt from its id ("model-de-en") for removal. */
        fun stale(id: String) = id.split('-').let { Component(id, it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }, "", 0, "") }
        private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

        fun parse(json: String): List<Component> {
            val a = JSONObject(json).getJSONArray("components")
            return (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
                val f = c.getJSONObject("file")
                Component(c.getString("id"), c.getString("kind"), c.optString("from"), c.optString("to"),
                    f.getString("name"), f.getLong("size"), f.getString("sha256"))
            }
        }

        /** The language pairs offered: one per dictionary. */
        fun pairs(cs: List<Component>) = cs.filter { it.kind == "dict" }.map { it.from to it.to }

        /** Everything the chosen pairs need: their dictionaries, the models (through English), langid. */
        fun needed(cs: List<Component>, pairs: Collection<Pair<String, String>>): List<Component> {
            val ids = pairs.flatMap { (x, y) -> listOf("dict-$x-$y") + Engine.steps(x, y).map { (a, b) -> "model-$a-$b" } } +
                if (pairs.isEmpty()) emptyList() else listOf("langid")
            return cs.filter { it.id in ids.toSet() }
        }
    }
}
