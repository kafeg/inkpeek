package io.github.kafeg.inkpeek.mt

import io.github.kafeg.inkpeek.packs.Packs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test fun stepsGoThroughEnglish() {
        assertEquals(listOf("de" to "en", "en" to "ru"), Engine.steps("de", "ru"))
        assertEquals(listOf("en" to "ru"), Engine.steps("en", "ru"))
        assertEquals(emptyList<Pair<String, String>>(), Engine.steps("de", "de"))
    }

    /** The pair German → Russian needs the de→ru dictionary, de→en + en→ru models and langid — nothing else. */
    @Test fun neededComponents() {
        fun c(id: String, kind: String, from: String = "", to: String = "") = JSONObject().put("id", id).put("kind", kind)
            .put("from", from).put("to", to).put("file", JSONObject().put("name", "$id.zip").put("size", 1).put("sha256", "x"))
        val all = JSONArray().put(c("grammar-de", "grammar", "de")).put(c("grammar-en", "grammar", "en"))
            .put(c("dict-de-ru", "dict", "de", "ru")).put(c("dict-de-en", "dict", "de", "en")).put(c("dict-en-ru", "dict", "en", "ru"))
            .put(c("model-de-en", "model", "de", "en")).put(c("model-en-ru", "model", "en", "ru")).put(c("model-en-de", "model", "en", "de"))
            .put(c("langid", "langid"))
        val cs = Packs.parse(JSONObject().put("components", all).toString())
        assertEquals(listOf("dict-de-ru", "model-de-en", "model-en-ru", "langid"), Packs.needed(cs, listOf("de" to "ru")).map { it.id })
        assertEquals(listOf("de" to "ru", "de" to "en", "en" to "ru"), Packs.pairs(cs))
        assertEquals(emptyList<String>(), Packs.needed(cs, emptyList()).map { it.id })
    }

    /** A component gone from the packs list removes only its own folder, never the whole models folder. */
    @Test fun staleRemovesOnlyItsOwn() {
        val root = kotlin.io.path.createTempDirectory().toFile(); val models = java.io.File(root, "models")
        java.io.File(models, "deen").mkdirs(); java.io.File(models, "enru").mkdirs()
        val packs = Packs(java.io.File(root, "packs"), models)
        packs.remove(Packs.stale("model-de-en")); packs.remove(Packs.stale("model"))
        assertEquals(listOf("enru"), models.list()!!.toList())
        root.deleteRecursively()
    }
}

/** The AI model's context is small: long text goes in pieces of whole sentences, nothing lost. */
class ChunksTest {
    @Test fun longTextInWholeSentences() {
        val s = (1..60).map { "Satz Nummer $it ist hier." }
        val pieces = io.github.kafeg.inkpeek.mt.Llm.chunks(s.joinToString(" "), "de", max = 120)
        org.junit.Assert.assertTrue(pieces.all { it.length <= 120 })
        org.junit.Assert.assertEquals(s, pieces.flatMap { p -> Regex("Satz Nummer \\d+ ist hier\\.").findAll(p).map { it.value }.toList() })
        org.junit.Assert.assertEquals(listOf("Kurz."), io.github.kafeg.inkpeek.mt.Llm.chunks("Kurz.", "de"))
    }
}
