package io.github.kafeg.inkpeek.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shared cases in tests/sync (LexiPeek runs them too): every case folder found. */
class SyncCasesTest {
    private val root = File("../tests/sync")
    private fun cases(kind: String) = File(root, kind).listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        .also { assertTrue("no $kind cases", it.isNotEmpty()) }

    /** A list of objects as a set, each object by its sorted keys. */
    private fun set(a: JSONArray) = (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> o.keySet().sorted().associateWith { o.get(it).toString() } } }.toSet()

    /** The merged state as expected.json has it. */
    private fun json(s: State) = JSONObject()
        .put("saved", JSONArray(s.saved.map { JSONObject().put("text", it.text).put("kind", it.kind).put("lang", it.lang).put("meaning", it.meaning)
            .put("created", it.created).put("id", it.id).put("origin", it.origin).put("updated_by", it.updatedBy) }))
        .put("history", JSONArray(s.history.map { JSONObject().put("text", it.text).put("kind", it.kind).put("lang", it.lang)
            .put("base", it.base ?: JSONObject.NULL).put("meaning", it.meaning).put("seen", it.seen).put("count", it.count).put("devices", JSONArray(it.devices)) }))
        .put("ai", JSONArray(s.ai.map { JSONObject().put("text", it.text).put("lang", it.lang).put("section", it.section).put("ver", it.ver)
            .put("model", it.model).put("answer", it.answer).put("created", it.created) }))

    /** What differs from expected.json: per list, the entries only it has and the entries only the merge has. */
    private fun diff(c: File, s: State): String {
        val want = JSONObject(File(c, "expected.json").readText()); val got = json(s)
        return listOf("saved", "history", "ai").joinToString("") { k ->
            val w = set(want.getJSONArray(k)); val g = set(got.getJSONArray(k))
            if (w == g) "" else "${c.name} $k:\n  expected only: ${w - g}\n  merged only:   ${g - w}\n"
        }
    }

    private fun check(c: File, s: State) = diff(c, s).let { assertTrue(it, it.isEmpty()) }

    @Test fun reader() = cases("reader").forEach { c -> check(c, SyncLog.merge(SyncLog.read(FileFolder(c)))) }

    /** Folders and files in another order. */
    private class Reversed(private val f: Folder) : Folder by f {
        override fun devices() = f.devices().reversed()
        override fun files(device: String) = f.files(device).reversed()
    }

    /** One device's lines per the format's compaction: its newest delete and what follows it, its newest look, its newest answer per key. */
    private fun compact(own: List<Line>): List<Line> = own.sortedWith(SyncLog.ORDER).groupBy { it.text }.values.flatMap { ls ->
        val s = ls.filter { it.op.startsWith("saved.") }; val h = ls.filter { it.op.startsWith("history.") }
        val hd = h.indexOfLast { it.op == "history.del" }
        s.drop(maxOf(0, s.indexOfLast { it.op == "saved.del" })) + listOfNotNull(h.getOrNull(hd), h.drop(hd + 1).lastOrNull { it.op == "history.seen" }) +
            ls.filter { it.op == "ai.put" }.associateBy { listOf(it.lang, it.section, it.ver) }.values
    }

    private fun State.asSets() = Triple(saved.toSet(), history.toSet(), ai.toSet())

    /** The merge does not depend on the order of files and lines, a line read twice, or a device compacting its log. */
    private fun properties(c: File, lines: List<Line>, s: State) {
        val want = s.asSets()
        assertEquals("${c.name}: file order", want, SyncLog.merge(SyncLog.read(Reversed(FileFolder(c)))).asSets())
        assertEquals("${c.name}: line order", want, SyncLog.merge(lines.shuffled(java.util.Random(c.name.hashCode().toLong()))).asSets())
        assertEquals("${c.name}: read twice", want, SyncLog.merge(lines + lines.reversed()).asSets())
        val compacted = lines.groupBy { it.d }.values.flatMap(::compact)
        assertEquals("${c.name}: compacted", want, SyncLog.merge(compacted).asSets())
        assertEquals("${c.name}: compacted again", compacted.toSet(), lines.groupBy { it.d }.values.flatMap { compact(compact(it)) }.toSet())
    }

    /** tests/sync/generated (tests/sync/generate.py): the merge and its properties on every case. */
    @Test fun generated() = (File(root, "generated").listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        .also { assertTrue("no generated cases", it.size >= 20) }).joinToString("") { c ->
        val lines = SyncLog.read(FileFolder(c)); val s = SyncLog.merge(lines)
        diff(c, s).also { if (it.isEmpty()) properties(c, lines, s) }
    }.let { assertTrue(it, it.isEmpty()) }

    /** INKPEEK_STRESS=1: a case of 50,000 lines made now (python3), the same checks. */
    @Test fun stress() {
        if (System.getenv("INKPEEK_STRESS") != "1") return
        val dir = File.createTempFile("stress", "").apply { delete(); mkdirs() }
        val p = ProcessBuilder("python3", File(root, "generate.py").path, "--stress", dir.path).inheritIO().start()
        assertEquals("generate.py --stress", 0, p.waitFor())
        val c = File(dir, "stress"); val lines = SyncLog.read(FileFolder(c)); val s = SyncLog.merge(lines)
        check(c, s); properties(c, lines, s)
        dir.deleteRecursively()
    }

    @Test fun identity() = cases("identity").forEach { c ->
        val local = JSONObject(File(c, "local.json").readText())
        val id = local.getString("device")
        val own = SyncLog.read(FileFolder(c), id).filter { it.d == id }
        val keep = SyncLog.keep(own, local.opt("last") as? String)
        assertEquals(c.name, JSONObject(File(c, "expected.json").readText()).getString("decision"), if (keep) "keep" else "new")
    }

    @Test fun writer() = cases("writer").forEach { c ->
        val sc = JSONObject(File(c, "scenario.json").readText())
        val dir = File.createTempFile("sync", "").apply { delete(); mkdirs() }
        File(c, "before").takeIf { it.isDirectory }?.copyRecursively(dir)
        val id = sc.getString("device")
        val w = Writer(FileFolder(dir), id, sc.getString("app"), sc.getString("name"), sc.getLong("created")).apply { read() }
        val steps = sc.getJSONArray("steps")
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            fun f(k: String) = s.optString(k)
            w.write(listOf(Line(0, 0, "", f("op"), f("text"), f("kind"), f("lang"), f("meaning"), f("base"), 0, f("section"),
                s.optLong("ver"), f("model"), f("answer"))), s.getLong("now"))
        }
        val want = File(c, "after/inkpeek-log/$id"); val got = File(dir, "inkpeek-log/$id")
        assertEquals(c.name, want.list()!!.sorted(), got.list()!!.sorted())
        want.listFiles()!!.forEach { assertArrayEquals("${c.name}/${it.name}", it.readBytes(), File(got, it.name).readBytes()) }
        dir.deleteRecursively()
    }
}
