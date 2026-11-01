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

    @Test fun reader() = cases("reader").forEach { c ->
        val s = SyncLog.merge(SyncLog.read(FileFolder(c)))
        val got = JSONObject()
            .put("saved", JSONArray(s.saved.map { JSONObject().put("text", it.text).put("kind", it.kind).put("lang", it.lang).put("meaning", it.meaning)
                .put("created", it.created).put("id", it.id).put("origin", it.origin).put("updated_by", it.updatedBy) }))
            .put("history", JSONArray(s.history.map { JSONObject().put("text", it.text).put("kind", it.kind).put("lang", it.lang)
                .put("base", it.base ?: JSONObject.NULL).put("meaning", it.meaning).put("seen", it.seen).put("count", it.count).put("devices", JSONArray(it.devices)) }))
            .put("ai", JSONArray(s.ai.map { JSONObject().put("text", it.text).put("lang", it.lang).put("section", it.section).put("ver", it.ver)
                .put("model", it.model).put("answer", it.answer).put("created", it.created) }))
        val want = JSONObject(File(c, "expected.json").readText())
        for (k in listOf("saved", "history", "ai")) assertEquals("${c.name}: $k", set(want.getJSONArray(k)), set(got.getJSONArray(k)))
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
