package io.github.kafeg.inkpeek.sync

import org.json.JSONArray
import org.json.JSONObject

/** One line of the sync log (docs/formats.md, "Sync log"); [raw]: its bytes without "\n". A missing field reads as "" or 0. */
data class Line(
    val ms: Long, val n: Long, val d: String, val op: String, val text: String,
    val kind: String = "", val lang: String = "", val meaning: String = "", val base: String = "", val c: Long = 0,
    val section: String = "", val ver: Long = 0, val model: String = "", val answer: String = "", val raw: String = "",
)

/** The merged records, with what only the log knows (`id`, `origin`, `updated_by`, `devices`). */
data class Saved(val text: String, val kind: String, val lang: String, val meaning: String, val created: Long,
                 val id: String, val origin: String, val updatedBy: String)
data class Seen(val text: String, val kind: String, val lang: String, val base: String?, val meaning: String,
                val seen: Long, val count: Long, val devices: List<String>)
data class Answer(val text: String, val lang: String, val section: String, val ver: Long, val model: String,
                  val answer: String, val created: Long)
data class State(val saved: List<Saved>, val history: List<Seen>, val ai: List<Answer>)

/** `inkpeek-log/` of a sync folder: a folder per device. */
interface Folder {
    fun devices(): List<String>
    /** The files (not folders) right in `inkpeek-log/<device>/`. */
    fun files(device: String): List<String>
    fun read(device: String, name: String): ByteArray?
    fun write(device: String, name: String, bytes: ByteArray)
    /** False where the folder cannot append: the file is then written whole. */
    fun append(device: String, name: String, bytes: ByteArray): Boolean
}

class FileFolder(private val root: java.io.File) : Folder {
    private fun dir(d: String) = java.io.File(root, "inkpeek-log/$d").also { it.mkdirs() }
    override fun devices() = java.io.File(root, "inkpeek-log").listFiles { f -> f.isDirectory }.orEmpty().map { it.name }
    override fun files(device: String) = java.io.File(root, "inkpeek-log/$device").listFiles { f -> f.isFile }.orEmpty().map { it.name }
    override fun read(device: String, name: String) = java.io.File(root, "inkpeek-log/$device/$name").takeIf { it.isFile }?.readBytes()
    override fun write(device: String, name: String, bytes: ByteArray) = java.io.File(dir(device), name).writeBytes(bytes)
    override fun append(device: String, name: String, bytes: ByteArray) = true.also { java.io.File(dir(device), name).appendBytes(bytes) }
}

object SyncLog {
    val OPS = mapOf(
        "saved.put" to listOf("text", "kind", "lang", "meaning"),
        "saved.del" to listOf("text"),
        "history.seen" to listOf("text", "kind", "lang", "meaning", "base", "c"),
        "history.del" to listOf("text"),
        "ai.put" to listOf("text", "lang", "section", "ver", "model", "answer"),
    )

    /** "Newest" is last: t, then the device, then the bytes. */
    val ORDER = compareBy<Line>({ it.ms }, { it.n }, { it.d }, { it.raw })

    /** Month files and the copies sync tools make on a conflict; not `.tmp`, `.syncthing.*`. */
    fun logFile(name: String) = name.endsWith(".jsonl") && !name.startsWith(".")

    /** The month file of [ms], UTC. */
    fun month(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).let { "%04d-%02d.jsonl".format(it.year, it.monthValue) }

    /** The complete lines of a file: what follows the last "\n" is being written or was cut. */
    fun lines(bytes: ByteArray): List<Line> {
        val s = String(bytes, Charsets.UTF_8)
        return s.substring(0, s.lastIndexOf('\n') + 1).split('\n').mapNotNull { parse(it) }
    }

    fun parse(raw: String): Line? = runCatching {
        if (!raw.startsWith("{")) return null
        val j = JSONObject(raw)
        val t = j.opt("t") as? JSONArray ?: return null
        if (t.length() != 2 || (0..1).any { t.get(it) !is Int && t.get(it) !is Long }) return null
        val op = j.opt("op") as? String ?: return null
        val d = j.opt("d") as? String ?: return null
        val text = j.opt("text") as? String ?: return null
        if (op !in OPS) return null
        fun s(k: String) = (j.opt(k) as? String).orEmpty()
        fun n(k: String) = (j.opt(k) as? Number)?.toLong() ?: 0
        Line(t.getLong(0), t.getLong(1), d, op, text, s("kind"), s("lang"), s("meaning"), s("base"), n("c"),
            s("section"), n("ver"), s("model"), s("answer"), raw)
    }.getOrNull()

    /** The canonical form: compact; keys t, d, op, then the op's fields in their order; `base` only when there is one. */
    fun format(l: Line): String = buildString {
        append("{\"t\":[").append(l.ms).append(',').append(l.n).append("],\"d\":"); str(l.d); append(",\"op\":"); str(l.op)
        for (k in OPS.getValue(l.op)) {
            if (k == "base" && l.base.isEmpty()) continue
            append(",\"").append(k).append("\":")
            when (k) {
                "c" -> append(l.c); "ver" -> append(l.ver)
                else -> str(when (k) { "text" -> l.text; "kind" -> l.kind; "lang" -> l.lang; "meaning" -> l.meaning
                    "base" -> l.base; "section" -> l.section; "model" -> l.model; else -> l.answer })
            }
        }
        append('}')
    }

    fun device(id: String, app: String, name: String, created: Long) = buildString {
        append("{\"device\":"); str(id); append(",\"app\":"); str(app); append(",\"name\":"); str(name); append(",\"created\":").append(created).append('}')
    }

    private fun StringBuilder.str(s: String) {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000c' -> append("\\f")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
        }
        append('"')
    }

    /** The lines of every device folder ([device]: of that one only), byte-equal ones once. */
    fun read(f: Folder, device: String? = null): List<Line> = (device?.let { listOf(it) } ?: f.devices())
        .flatMap { d -> f.files(d).filter(::logFile).flatMap { lines(f.read(d, it) ?: ByteArray(0)) } }.distinctBy { it.raw }

    fun merge(lines: Collection<Line>): State {
        val saved = ArrayList<Saved>(); val history = ArrayList<Seen>(); val ai = HashMap<List<Any>, Line>()
        for ((text, ls) in lines.distinctBy { it.raw }.sortedWith(ORDER).groupBy { it.text }) {
            val s = ls.filter { it.op.startsWith("saved.") }
            val win = s.lastOrNull()
            if (win?.op == "saved.put") {
                val first = s.drop(s.indexOfLast { it.op == "saved.del" } + 1).first()
                saved += Saved(text, win.kind, win.lang, win.meaning, win.ms, "${first.d}:${first.ms}:${first.n}", first.d, win.d)
            }
            // A delete drops the looks at or before it.
            val h = ls.filter { it.op.startsWith("history.") }.let { h -> h.drop(h.indexOfLast { it.op == "history.del" } + 1) }
            h.lastOrNull()?.let { top -> history += Seen(text, top.kind, top.lang, top.base.ifEmpty { null }, top.meaning, top.ms,
                h.groupBy { it.d }.values.sumOf { v -> v.maxOf { it.c } }, h.map { it.d }.distinct().sorted()) }
            ls.filter { it.op == "ai.put" }.forEach { ai[listOf(it.text, it.lang, it.section, it.ver)] = it }
        }
        val kept = saved.map { it.text }.toSet() + history.map { it.text }
        return State(saved, history, ai.values.filter { it.text in kept }.map { Answer(it.text, it.lang, it.section, it.ver, it.model, it.answer, it.ms) })
    }

    /** Two installs with one id: keep it unless the own lines with the newest t are other than [last]. */
    fun keep(own: List<Line>, last: String?): Boolean {
        val top = own.maxWithOrNull(compareBy({ it.ms }, { it.n })) ?: return true
        return own.filter { it.ms == top.ms && it.n == top.n }.map { it.raw }.distinct() == listOf(last)
    }
}

/**
 * A device's view of the log: every line read, the clock, its newest own line; [write] adds its lines to its folder.
 * [name] and [created] go into its device.json.
 */
class Writer(private val folder: Folder, val device: String, private val app: String, private val name: String, private val created: Long) {
    private val byText = HashMap<String, MutableList<Line>>()
    private val known = HashSet<String>()
    private var clockMs = 0L
    private var clockN = -1L
    private var hasDevice = false
    /** The own line with the newest t: the app keeps it as `last`. */
    var last: Line? = null; private set

    val lines: Collection<Line> get() = byText.values.flatten()

    fun read() { see(SyncLog.read(folder)); hasDevice = "device.json" in folder.files(device) }

    fun see(ls: Collection<Line>) = ls.forEach { l ->
        if (!known.add(l.raw)) return@forEach
        byText.getOrPut(l.text) { ArrayList() } += l
        if (l.ms > clockMs || (l.ms == clockMs && l.n > clockN)) { clockMs = l.ms; clockN = l.n }
        if (l.d == device && last.let { it == null || SyncLog.ORDER.compare(l, it) > 0 }) last = l
    }

    /** This device's looks of [text] since the newest delete of it. */
    fun looks(text: String): Long {
        val ls = byText[text].orEmpty().filter { it.op.startsWith("history.") }.sortedWith(SyncLog.ORDER)
        return ls.drop(ls.indexOfLast { it.op == "history.del" } + 1).filter { it.d == device }.maxOfOrNull { it.c } ?: 0
    }

    /**
     * [drafts] (lines without t, d, c) as this device's: t from the clock at [now], or the record's own time from [at]
     * (a record made before sync); c counted on. One read and one write per month file.
     */
    fun write(drafts: List<Line>, now: Long, at: (Line) -> Long? = { null }): List<Line> {
        val made = drafts.map { dr ->
            val (ms, n) = at(dr)?.let { own -> own to (byText[dr.text].orEmpty().filter { it.ms == own }.maxOfOrNull { it.n + 1 } ?: 0) }
                ?: maxOf(clockMs, now).let { it to if (it == clockMs) clockN + 1 else 0 }
            val l = dr.copy(ms = ms, n = n, d = device, c = if (dr.op == "history.seen") dr.c + looks(dr.text) + 1 else 0)
            l.copy(raw = SyncLog.format(l)).also { see(listOf(it)) }
        }
        if (made.isEmpty()) return made
        if (!hasDevice) { folder.write(device, "device.json", (SyncLog.device(device, app, name, created) + "\n").toByteArray()); hasDevice = true }
        for ((file, ls) in made.groupBy { SyncLog.month(it.ms) }) {
            val add = ls.joinToString("") { it.raw + "\n" }.toByteArray()
            val old = folder.read(device, file)
            // A line cut by a crash goes first.
            val cut = old != null && old.isNotEmpty() && old.last() != '\n'.code.toByte()
            if (cut) folder.write(device, file, old!!.copyOf(old.lastIndexOf('\n'.code.toByte()) + 1) + add)
            else if (old == null || !folder.append(device, file, add)) folder.write(device, file, (old ?: ByteArray(0)) + add)
        }
        return made
    }
}
