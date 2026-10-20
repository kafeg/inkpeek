package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

/** The card's core over pack-like dictionaries (dict-<x>-<y>, first line in <p class="head">). */
class LibraryTest {
    private fun pack(root: File, id: String, articles: Map<String, String>, syn: Map<String, String> = emptyMap()) {
        val dir = File(root, id).apply { mkdirs() }
        val words = articles.keys.sortedBy { normKey(it) }
        val data = ByteArrayOutputStream(); val idx = ByteArrayOutputStream(); val o = DataOutputStream(idx)
        words.forEach { w ->
            val b = "<p class=\"head\">${articles.getValue(w)}</p><ol><li>…</li></ol>".toByteArray()
            o.write(w.toByteArray()); o.write(0); o.writeInt(data.size()); o.writeInt(b.size); data.write(b)
        }
        File(dir, "$id.idx").writeBytes(idx.toByteArray()); File(dir, "$id.dict").writeBytes(data.toByteArray())
        val s = ByteArrayOutputStream(); DataOutputStream(s).use { d -> syn.forEach { (f, h) -> d.write(f.toByteArray()); d.write(0); d.writeInt(words.indexOf(h)) } }
        File(dir, "$id.syn").writeBytes(s.toByteArray())
        File(dir, "$id.ifo").writeText("StarDict's dict ifo file\nversion=3.0.0\nbookname=InkPeek $id\nwordcount=${words.size}\nsametypesequence=h\n")
    }

    private fun library(): Library {
        val root = Files.createTempDirectory("packs").toFile()
        pack(root, "dict-de-ru", mapOf("Haus" to "<b>das Haus</b> (Hauses, мн. Häuser) — дом, здание", "Gift" to "<b>das Gift</b> — яд",
            "Hund" to "<b>der Hund</b> — собака", "Er" to "<b>der Er</b> — мужчина", "er" to "<b>er</b> — он"), mapOf("Häuser" to "Haus"))
        pack(root, "dict-de-en", mapOf("Haus" to "<b>das Haus</b> (Hauses, pl. Häuser) — house", "Hund" to "<b>der Hund</b> — dog"), mapOf("Häuser" to "Haus"))
        pack(root, "dict-en-ru", mapOf("gift" to "<b>gift</b> — подарок", "die" to "<b>die</b> (died) — умирать"))
        return Library(listOf(root), File(root, "work")).also { lib -> lib.all().forEach { it.prepare() } }
    }

    @Test fun headsPerBookLanguageAnswerFirst() {
        val lib = library()
        assertEquals(listOf(Head("Haus", "das Haus (Hauses, мн. Häuser) — дом, здание")), lib.heads("Häuser", "ru"))
        assertEquals("das Haus (Hauses, pl. Häuser)", lib.heads("Häuser", "en").first().header)
        // "Gift" is German and English: the German header first for Russian answers, the English one under it.
        assertEquals(listOf("das Gift", "gift"), lib.heads("Gift", "ru").map { it.header })
        assertEquals(listOf("dict-de-en", "dict-de-ru"), lib.lookup("Hund", "en").map { it.first.ifo.parentFile!!.name })
    }

    @Test fun glossPicksTheTextsLanguageIntoTheAnswer() {
        val g = library().gloss("Die Häuser, der Hund", "ru")
        assertEquals(Gloss("Häuser", Head("Haus", "das Haus (Hauses, мн. Häuser) — дом, здание")), g.first { it.word == "Häuser" })
        assertEquals("собака", g.first { it.word == "Hund" }.meaning)
        // A sentence's first word is looked up in lower case first; inside a sentence the case stays.
        assertEquals("он", library().gloss("Er kam. Er ging", "ru").first().meaning)
        assertEquals("мужчина", library().gloss("Da kam der Er", "ru").first { it.word == "Er" }.meaning)
    }
}
