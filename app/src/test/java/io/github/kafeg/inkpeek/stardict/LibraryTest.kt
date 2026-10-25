package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

/** The card's core over pack-like dictionaries (dict-<x>-<y>, first line in <p class="head">). */
class LibraryTest {
    private fun pack(root: File, id: String, articles: Map<String, String>, syn: Map<String, String> = emptyMap(),
                     article: (String) -> String = { "<p class=\"head\">$it</p><ol><li>…</li></ol>" }) {
        val dir = File(root, id).apply { mkdirs() }
        val words = articles.keys.sortedBy { normKey(it) }
        val data = ByteArrayOutputStream(); val idx = ByteArrayOutputStream(); val o = DataOutputStream(idx)
        words.forEach { w ->
            val b = article(articles.getValue(w)).toByteArray()
            o.write(w.toByteArray()); o.write(0); o.writeInt(data.size()); o.writeInt(b.size); data.write(b)
        }
        File(dir, "$id.idx").writeBytes(idx.toByteArray()); File(dir, "$id.dict").writeBytes(data.toByteArray())
        val s = ByteArrayOutputStream(); DataOutputStream(s).use { d -> syn.forEach { (f, h) -> d.write(f.toByteArray()); d.write(0); d.writeInt(words.indexOf(h)) } }
        File(dir, "$id.syn").writeBytes(s.toByteArray())
        File(dir, "$id.ifo").writeText("StarDict's dict ifo file\nversion=3.0.0\nbookname=InkPeek $id\nwordcount=${words.size}\nsametypesequence=h\n")
    }

    private fun library(own: (File) -> Unit = {}, use: (StarDict, Boolean) -> Boolean = { _, _ -> true }, order: () -> List<String> = { emptyList() }): Library {
        val root = Files.createTempDirectory("packs").toFile()
        val mine = Files.createTempDirectory("own").toFile().also(own)
        pack(root, "dict-de-ru", mapOf("Haus" to "<b>das Haus</b> (Hauses, мн. Häuser) — дом, здание", "Gift" to "<b>das Gift</b> — яд",
            "Hund" to "<b>der Hund</b> — собака", "Er" to "<b>der Er</b> — мужчина", "er" to "<b>er</b> — он"), mapOf("Häuser" to "Haus"))
        pack(root, "dict-de-en", mapOf("Haus" to "<b>das Haus</b> (Hauses, pl. Häuser) — house", "Hund" to "<b>der Hund</b> — dog"), mapOf("Häuser" to "Haus"))
        pack(root, "dict-en-ru", mapOf("gift" to "<b>gift</b> — подарок", "die" to "<b>die</b> (died) — умирать"))
        return Library(root, listOf(mine), File(root, "work"), use, order).also { lib -> lib.found().forEach { it.prepare() } }
    }

    @Test fun headsPerBookLanguageAnswerFirst() {
        val lib = library()
        assertEquals(listOf(Head("Haus", "das Haus (Hauses, мн. Häuser) — дом, здание", "de")), lib.heads("Häuser", "ru"))
        assertEquals("das Haus (Hauses, pl. Häuser)", lib.heads("Häuser", "en").first().header)
        // "Gift" is German and English: the German header first for Russian answers, the English one under it.
        assertEquals(listOf("das Gift", "gift"), lib.heads("Gift", "ru").map { it.header })
        assertEquals(listOf("das Haus (Hauses, pl. Häuser)"), lib.heads("Haus", "en").map { it.header }) // not the Russian one
        // Only the dictionaries into the app's language; the others when none of those knows the word.
        assertEquals(listOf("dict-de-en"), lib.lookup("Hund", "en").map { it.first.ifo.parentFile!!.name })
        assertEquals(listOf("dict-de-ru", "dict-en-ru"), lib.lookup("Gift", "en").map { it.first.ifo.parentFile!!.name })
    }

    @Test fun glossPicksTheTextsLanguageIntoTheAnswer() {
        val g = library().gloss("Die Häuser, der Hund", "ru")
        assertEquals(Gloss("Häuser", Head("Haus", "das Haus (Hauses, мн. Häuser) — дом, здание")), g.first { it.word == "Häuser" })
        assertEquals("собака", g.first { it.word == "Hund" }.meaning)
        // A sentence's first word is looked up in lower case first; inside a sentence the case stays.
        assertEquals("он", library().gloss("Er kam. Er ging", "ru").first().meaning)
        assertEquals("мужчина", library().gloss("Da kam der Er", "ru").first { it.word == "Er" }.meaning)
    }

    /** A KOReader-like dictionary: plain articles, no pack first line, no languages in its folder name. */
    private fun mueller(dir: File) = pack(dir, "Mueller", mapOf("Haus" to "дом; здание", "Katze" to "кошка"), article = { "<b>Haus</b><br>1. $it" })

    @Test fun ownDictionariesComeAfterThePacks() {
        val lib = library(::mueller)
        assertEquals(listOf("dict-de-ru", "Mueller"), lib.lookup("Haus", "ru").map { it.first.ifo.parentFile!!.name })
        assertEquals(listOf(false, true), lib.lookup("Haus", "ru").map { !lib.isPack(it.first) })
        // Not a pack: no languages, no header of its own.
        assertEquals(null, lib.found().first { !lib.isPack(it) }.book)
        assertEquals(listOf("das Haus (Hauses, мн. Häuser)"), lib.heads("Haus", "ru").map { it.header })
        // The gloss takes an own dictionary's first meaning where no pack knows the word.
        assertEquals("дом, здание", lib.gloss("Haus", "ru").first().meaning) // a pack first
        assertEquals(Head("Katze", "Katze — кошка"), lib.gloss("Katze", "ru").first().head)
    }

    @Test fun switchedOffDictionariesAreNotUsed() {
        // The packs' dictionaries off: only the own ones.
        val lib = library(::mueller, use = { _, pack -> !pack })
        assertEquals(listOf("Mueller"), lib.lookup("Haus", "ru").map { it.first.ifo.parentFile!!.name })
        assertEquals(4, lib.found().size)
        assertEquals(1, lib.all().size)
    }

    /** An own dictionary's index stays while its folder is away (a card not mounted), goes when its file is gone from it. */
    @Test fun anIndexGoesOnlyWithItsDictionary() {
        val root = Files.createTempDirectory("packs").toFile(); val mine = Files.createTempDirectory("own").toFile().also(::mueller)
        val work = File(root, "work")
        fun lib() = Library(root, listOf(mine), work, { _, _ -> true }) { emptyList() }.also { l -> l.found().forEach { it.prepare() } }
        val id = lib().found().single().id
        val away = File(mine.path + "-away"); mine.renameTo(away)
        lib(); assertEquals(true, File(work, id).isDirectory)
        away.renameTo(mine); mine.walkTopDown().filter { it.isFile }.forEach { it.delete() }
        lib(); assertEquals(false, File(work, id).exists())
    }

    @Test fun theOrderIsThePersons() {
        var order = emptyList<String>()
        val lib = library(::mueller, order = { order })
        assertEquals(listOf("dict-de-ru", "Mueller"), lib.lookup("Haus", "ru").map { it.first.ifo.parentFile!!.name })
        // Mueller first: before the pack, the others after both in the old order.
        order = listOf(lib.found().first { !lib.isPack(it) }.id)
        assertEquals(listOf("Mueller", "dict-de-ru"), lib.lookup("Haus", "ru").map { it.first.ifo.parentFile!!.name })
    }
}
