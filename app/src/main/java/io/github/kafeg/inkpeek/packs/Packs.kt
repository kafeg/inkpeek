package io.github.kafeg.inkpeek.packs

import com.github.luben.zstd.ZstdInputStream
import io.github.kafeg.inkpeek.mt.Engine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * One downloadable part of the language packs (see packs/README.md): dict, model (Mozilla), langid, opus (OPUS-MT) or
 * llm (an AI translation model). [covers]: the directions an opus / llm model translates ("de-ru"); [tags]: the token a
 * multi-target OPUS-MT model needs before a sentence, by target; [ramMb] / [cpu]: what a device must have; [mirror]: the
 * original file, tried before the release; [coverage]: a dictionary's share of the book language's 5,000 most frequent words (0: unknown).
 */
data class Component(val id: String, val kind: String, val from: String, val to: String, val file: String,
                     val size: Long, val sha256: String, val covers: List<String> = emptyList(), val tags: Map<String, String> = emptyMap(),
                     val ramMb: Int = 0, val cpu: List<String> = emptyList(), val template: String = "", val names: Map<String, String> = emptyMap(),
                     val mirror: String = "", val name: String = "", val coverage: Double = 0.0)

enum class Level { FULL, BASIC, EXPERIMENTAL }

/** Too little room for a download, its unpacked files and the old version together, in MB. */
class NoSpace(val needMb: Long, val freeMb: Long) : Exception("Not enough free space: $needMb MB needed, $freeMb MB free")

/**
 * InkPeek's language packs from this project's release "packs" — the only source the app downloads them from.
 * Layout under [root]: dicts/<id>/ (the card's dictionaries; the first line of an article is the header), langid.ftz,
 * installed.json (id → sha256); models go to [models]/<from><to>/ as the offline engine expects.
 */
class Packs(val root: File, val models: File, private val assets: String = ASSETS, private val http: OkHttpClient = io.github.kafeg.inkpeek.Net.http) {
    val dicts get() = File(root, "dicts")
    val langid get() = File(root, "langid.ftz")
    private val state get() = File(root, "installed.json")
    private val listFile get() = File(root, "packs.json")

    /** The packs list from the release; the last one received is kept, so installed packs show up offline too. */
    fun list(): List<Component> {
        val json = http.newCall(Request.Builder().url(assets + "packs.json").build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}"); it.body!!.string()
        }
        // The copy for offline use is a convenience: no access to files yet (a fresh install) must not hide the list.
        return parse(json).also { runCatching { root.mkdirs(); write(listFile, json) } }
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
        safe(c, install = true)
        // Left by a download the process did not live to finish (one install at a time: none is running).
        root.mkdirs(); File(root, "tmp").deleteRecursively(); root.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        // The download, the unpacked files and the old version side by side at the peak.
        val free = root.usableSpace; val need = if (c.kind == "llm") c.size + c.size / 10 else c.size * 3
        if (free < need) throw NoSpace(need / 1_000_000, free / 1_000_000)
        val tmp = File(root, c.file + ".part")
        try { download(c, tmp, progress) } catch (e: Throwable) { tmp.delete(); throw e }
        try {
            when (c.kind) {
                "dict" -> unzip(tmp, File(dicts, c.id))
                "model" -> untar(tmp, File(models, c.from + c.to))
                "langid" -> swap(tmp, langid)
                "opus" -> untar(tmp, opusDir(c.id))
                "llm" -> swap(tmp, llmFile(c.id))
                else -> error("Unknown pack kind ${c.kind}")
            }
        } finally { tmp.delete(); File(root, "tmp").deleteRecursively() }
        mark(c.id, c.sha256)
    }

    /** The original file first when there is one (a big model off our release), the release if that fails. */
    private fun download(c: Component, tmp: File, progress: (Long, Long) -> Unit) {
        if (c.mirror.isNotBlank()) {
            val first = runCatching { download(c, c.mirror, tmp, progress) }
            if (first.isSuccess) return
            if (first.exceptionOrNull() is java.util.concurrent.CancellationException) throw first.exceptionOrNull()!!
        }
        download(c, assets + c.file, tmp, progress)
    }

    private fun download(c: Component, url: String, tmp: File, progress: (Long, Long) -> Unit) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
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
        safe(c, install = false)
        when (c.kind) {
            "dict" -> File(dicts, c.id).deleteRecursively()
            "grammar" -> File(root, "grammar").deleteRecursively() // old builds installed grammar packs
            "model" -> if (c.from.isNotBlank() && c.to.isNotBlank()) File(models, c.from + c.to).deleteRecursively()
            "langid" -> langid.delete()
            "opus" -> opusDir(c.id).deleteRecursively()
            "llm" -> llmFile(c.id).delete()
        }
        mark(c.id, null)
    }

    /** An OPUS-MT model's folder and an AI model's file, next to the Mozilla models. */
    fun opusDir(id: String) = File(models, "opus/$id")
    fun llmFile(id: String) = File(models, "llm/$id.gguf")

    /** Installed pairs (from → to). */
    fun installedPairs(): List<Pair<String, String>> = installed().keys.filter { it.startsWith("dict-") }
        .map { it.removePrefix("dict-").let { p -> p.substringBefore('-') to p.substringAfter('-') } }


    /** Unpacked outside the library first: the card must not see a half-written dictionary. */
    private fun staging(dest: File) = File(root, "tmp/" + dest.name).also { it.deleteRecursively(); it.mkdirs() }

    /** The unpacked folder to its place; on a fresh device its parent (dicts/, models/) does not exist yet. */
    private fun place(tmp: File, dest: File) = swap(tmp, dest)

    /** [new] in place of [dest]: the old one goes aside (tmp/, not seen by the library) first and away last. */
    private fun swap(new: File, dest: File) {
        dest.parentFile?.mkdirs()
        val old = File(root, "tmp/old-" + dest.name); old.deleteRecursively(); old.parentFile?.mkdirs()
        if (dest.exists() && !dest.renameTo(old)) error("Cannot move ${dest.name}")
        if (!new.renameTo(dest)) { old.renameTo(dest); error("Cannot move ${dest.name}") }
        old.deleteRecursively()
    }

    /** Ids and languages become paths: only plain names (a broken list must not reach outside its folder). */
    private fun safe(c: Component, install: Boolean) {
        val name = Regex("[A-Za-z0-9._+-]+")
        require(name.matches(c.id) && ".." !in c.id) { "Bad pack id ${c.id}" }
        // A model without its languages is removed as nothing (an old entry); installed, it would be the whole models folder.
        if (c.kind == "model" && (install || c.from.isNotBlank() || c.to.isNotBlank()))
            require(name.matches(c.from) && name.matches(c.to) && ".." !in c.from + c.to) { "Bad model ${c.id}" }
    }

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
        place(tmp, dest)
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
        place(tmp, dest)
    }

    companion object {
        const val ASSETS = "https://github.com/kafeg/inkpeek/releases/download/packs/"

        /** An installed component the packs list no longer has, rebuilt from its id ("model-de-en") for removal. */
        fun stale(id: String) = id.split('-').let { Component(id, it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }, "", 0, "") }

        fun parse(json: String): List<Component> {
            val a = JSONObject(json).getJSONArray("components")
            return (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
                val f = c.getJSONObject("file")
                fun list(k: String) = c.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                fun map(k: String) = c.optJSONObject(k)?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
                Component(c.getString("id"), c.getString("kind"), c.optString("from"), c.optString("to"),
                    f.getString("name"), f.getLong("size"), f.getString("sha256"), list("covers"), map("tags"),
                    c.optInt("ram_mb"), list("cpu"), c.optString("template"), map("names"), c.optString("mirror"), c.optString("name"),
                    c.optJSONObject("stats")?.optDouble("coverage", 0.0) ?: 0.0)
            }
        }

        /** A pair's label: full (≥ 85 % of the frequent words and offline translation), basic (≥ 60 %), else experimental; null: unknown. */
        fun level(cs: List<Component>, p: Pair<String, String>): Level? {
            val d = cs.firstOrNull { it.kind == "dict" && it.from == p.first && it.to == p.second } ?: return null
            if (d.coverage <= 0) return null
            val steps = if ("en" in listOf(p.first, p.second)) listOf(p) else listOf(p.first to "en", "en" to p.second)
            val mt = steps.all { (a, b) -> cs.any { it.id == "model-$a-$b" } }
            return when { d.coverage >= 85 && mt -> Level.FULL; d.coverage >= 60 -> Level.BASIC; else -> Level.EXPERIMENTAL }
        }

        /** The pack kind of a translation method's own models ("direct": OPUS-MT, "llm"); null: Mozilla's, in every pair. */
        fun kind(method: String) = when (method) { "direct" -> "opus"; "llm" -> "llm"; else -> null }

        /** The language pairs offered: one per dictionary. */
        fun pairs(cs: List<Component>) = cs.filter { it.kind == "dict" }.map { it.from to it.to }

        /**
         * Everything the chosen pairs need: their dictionaries, the models (through English, always: the fallback), langid,
         * and the model of the translation [method] ("direct": OPUS-MT, "llm": an AI model) that covers each pair.
         */
        fun needed(cs: List<Component>, pairs: Collection<Pair<String, String>>, method: String = "fast"): List<Component> {
            val ids = pairs.flatMap { (x, y) -> listOf("dict-$x-$y") + Engine.steps(x, y).map { (a, b) -> "model-$a-$b" } } +
                if (pairs.isEmpty()) emptyList() else listOf("langid")
            val kind = kind(method)
            val extra = if (kind == null) emptyList() else pairs.mapNotNull { (x, y) -> cs.firstOrNull { it.kind == kind && "$x-$y" in it.covers }?.id }
            return cs.filter { it.id in (ids + extra).toSet() }
        }

        /** What removing [pair] frees: its parts no other installed pair needs (shared models and langid stay). */
        fun freed(cs: List<Component>, installed: Collection<Pair<String, String>>, pair: Pair<String, String>, method: String = "fast"): List<Component> {
            val keep = needed(cs, installed - pair, method).map { it.id }.toSet()
            return needed(cs, listOf(pair), method).filter { it.id !in keep }
        }

        /** Installed parts whose file in the release is newer. */
        fun updates(cs: List<Component>, installed: Map<String, String>) = cs.filter { installed[it.id].let { s -> s != null && s != it.sha256 } }
    }
}
