package io.github.kafeg.inkpeek.anki

import io.github.kafeg.inkpeek.sync.Sync
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shared Anki cases in tests/anki (LexiPeek runs them too): one word from both apps is one note with the same fields. */
class AnkiCasesTest {
    private fun cases(name: String) = JSONObject(File("../tests/anki/$name").readText()).getJSONArray("cases")
        .let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.also { assertTrue(name, it.size >= 50) }

    private fun failures(name: String, check: (JSONObject) -> String?) = cases(name).mapNotNull(check).let { assertTrue(it.take(20).joinToString("\n"), it.isEmpty()) }

    @Test fun keys() = failures("keys.json") { c ->
        val got = Anki.key(c.getString("text"), c.getString("lang"), c.getBoolean("history"), c.getString("answer"))
        if (got == c.getString("key")) null else "${JSONObject.quote(c.getString("text"))} ${c.getString("lang")}: ${c.getString("key")} ≠ $got"
    }

    /** The entry as InkPeek's store keeps it: the language by its English name. */
    @Test fun fields() = failures("fields.json") { c ->
        val n = Anki.note(c.getString("text"), c.getString("kind"), c.getString("meaning"), Sync.name(c.getString("lang")),
            c.opt("quick") as? String, c.getBoolean("history"), c.getString("answer"))
        val want = c.getJSONObject("fields").let { f -> f.keySet().associateWith { f.getString(it) } }
        if (want == n.fields) null else "${JSONObject.quote(c.getString("text"))}: $want ≠ ${n.fields}"
    }

    @Test fun dedupe() = failures("dedupe.json") { c ->
        val ns = c.getJSONArray("notes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val got = Anki.plan(ns.map { Anki.Found(it.getLong("id"), it.getString("key"), it.getString("audio")) })
        val audio = c.getJSONObject("audio").let { a -> a.keySet().associate { it.toLong() to a.getString(it) } }
        val deleted = c.getJSONArray("deleted").let { a -> (0 until a.length()).map { a.getLong(it) } }
        if (got == Anki.Plan(audio, deleted)) null else "$c: $got"
    }
}
