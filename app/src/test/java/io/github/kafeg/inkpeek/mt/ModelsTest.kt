package io.github.kafeg.inkpeek.mt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private fun rec(s: String, t: String, type: String, v: String, arch: String, filter: String = "") = JSONObject()
        .put("sourceLanguage", s).put("targetLanguage", t).put("fileType", type).put("version", v).put("architecture", arch)
        .put("name", "$type.$s$t").put("filter_expression", filter).put("decompressedHash", "h").put("decompressedSize", 10)
        .put("attachment", JSONObject().put("location", "x/$type$s$t$v$arch.zst").put("size", 5))

    /** The full model first, then the newest version; incomplete, alpha and nightly-only sets are skipped. */
    @Test fun picksBestCompleteSet() {
        val recs = JSONArray()
        listOf("model", "lex", "vocab").forEach { recs.put(rec("en", "ru", it, "3.1", "base-memory", "env.appinfo.OS == 'Android'")) }
        listOf("model", "lex", "vocab").forEach { recs.put(rec("en", "ru", it, "3.0", "base", "env.appinfo.OS != 'Android'")) }
        listOf("model", "lex").forEach { recs.put(rec("de", "en", it, "3.0", "base")) } // no vocab: incomplete
        listOf("model", "lex", "vocab").forEach { recs.put(rec("de", "en", it, "3.0", "base-memory")) }
        listOf("model", "lex", "vocab").forEach { recs.put(rec("be", "en", it, "3.0a1", "tiny", "env.channel == 'nightly'")) }
        val sets = Models.parse(JSONObject().put("data", recs).toString())
        assertEquals("base" to "3.0", sets.getValue("en" to "ru").first().let { it.arch to it.version })
        assertEquals("base-memory", sets.getValue("de" to "en").single().arch)
        assertTrue(("be" to "en") !in sets)
        assertEquals(listOf("de" to "en", "en" to "ru"), Engine.steps("de", "ru"))
        assertEquals(listOf("en" to "ru"), Engine.steps("en", "ru"))
    }
}
