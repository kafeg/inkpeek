package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class StarDictTest {
    /** Writes a tiny dictionary; the .idx is deliberately unsorted (broken upstream builds). */
    private fun dict(dir: File, gz: Boolean = true,
                     words: List<Pair<String, String>> = listOf("Haus" to "<b>Haus</b> n — дом", "abend" to "вечер", "Ärger" to "досада"),
                     syns: List<Pair<String, Int>> = listOf("Häuser" to 0)): File {
        val data = ByteArrayOutputStream(); val idx = DataOutputStream(ByteArrayOutputStream().also { })
        val idxBytes = ByteArrayOutputStream(); val o = DataOutputStream(idxBytes)
        words.forEach { (w, a) ->
            val off = data.size(); val b = a.toByteArray(); data.write(b)
            o.write(w.toByteArray()); o.write(0); o.writeInt(off); o.writeInt(b.size)
        }
        idx.close()
        File(dir, "t.idx").writeBytes(idxBytes.toByteArray())
        if (gz) GZIPOutputStream(File(dir, "t.dict.dz").outputStream()).use { it.write(data.toByteArray()) }
        else File(dir, "t.dict").writeBytes(data.toByteArray())
        val syn = ByteArrayOutputStream(); DataOutputStream(syn).use { o2 -> syns.forEach { (f, i) -> o2.write(f.toByteArray()); o2.write(0); o2.writeInt(i) } }
        File(dir, "t.syn").writeBytes(syn.toByteArray())
        return File(dir, "t.ifo").apply {
            writeText("StarDict's dict ifo file\nversion=3.0.0\nbookname=Test\nwordcount=3\nsametypesequence=h\n")
        }
    }

    @Test fun lookupExactCaseAndSyn() {
        val tmp = Files.createTempDirectory("sd").toFile()
        val d = StarDict(dict(tmp), File(tmp, "work"))
        assertEquals("<b>Haus</b> n — дом", d.lookup("Haus").single().html)
        assertEquals("Haus", d.lookup("haus").first().word)
        assertEquals("Haus", d.lookup("häuser").single().word)
        assertEquals("досада", d.lookup("ärger").single().html)
        assertEquals("вечер", d.lookup("Abend").single().html)
        assertTrue(d.lookup("nope").isEmpty())
    }

    @Test fun aSentencesFirstWordIsItsLowerCaseWord() {
        val tmp = Files.createTempDirectory("sd").toFile()
        val words = listOf("verdammt" to "чертовский", "verdammen" to "проклинать", "Lag" to "лаг", "liegen" to "лежать")
        val d = StarDict(dict(tmp, words = words, syns = listOf("verdammt" to 1, "lag" to 3)), File(tmp, "work"))
        assertEquals("verdammt", d.lookup("Verdammt").first().word)  // "Verdammt!" — the adjective, not verdammen
        assertEquals("verdammt", d.lookup("verdammt").first().word)
        assertEquals("liegen", d.lookup("lag").first().word)         // "lag" — a form of liegen before the noun
        assertEquals("Lag", d.lookup("Lag").first().word)
    }

    @Test fun indexRebuiltWhenBrokenOrFilesChange() {
        val tmp = Files.createTempDirectory("sd").toFile(); val work = File(tmp, "work")
        val ifo = dict(tmp, gz = false)
        val d = StarDict(ifo, work); d.prepare(); assertTrue(d.ready)
        val index = work.walkTopDown().first { it.name == "index.ipk" }
        // A cut index (the app killed while copying it): not ready, built again.
        index.writeBytes(index.readBytes().copyOf(12)); assertTrue(!StarDict(ifo, work).ready)
        StarDict(ifo, work).also { it.prepare(); assertTrue(it.ready) }
        // A new edition in place with the old .ifo time: its articles, not the old offsets.
        val t = ifo.lastModified(); File(tmp, "t.dict").appendText("x"); ifo.setLastModified(t)
        assertTrue(!d.ready)
        assertEquals("вечер", StarDict(ifo, work).lookup("abend").single().html)
        // An index from an older app (no stamp) is kept while newer than the .ifo.
        File(index.parentFile, "stamp").delete(); assertTrue(StarDict(ifo, work).ready)
        assertTrue(File(index.parentFile, "stamp").exists())
    }

    @Test fun plainDictAndQueryCleanup() {
        val tmp = Files.createTempDirectory("sd").toFile()
        val d = StarDict(dict(tmp, gz = false), File(tmp, "work"))
        assertEquals("вечер", d.lookup(cleanQuery("«abend»,")).single().html)
    }


    @Test fun articleTypedFields() {
        val b = ByteArrayOutputStream()
        b.write('m'.code); b.write("a < b".toByteArray()); b.write(0); b.write('h'.code); b.write("<i>x</i>".toByteArray()); b.write(0)
        assertEquals("a &lt; b<br><i>x</i>", Article.toHtml(b.toByteArray(), null))
    }

}

class MarkdownTest {
    @Test fun boldItalicLists() {
        org.junit.Assert.assertEquals("<b>Haus</b> — <i>дом</i><br>• a &lt; b",
            io.github.kafeg.inkpeek.ui.Ink.markdown("**Haus** — *дом*\n- a < b"))
    }
}

class TidyTest {
    @Test fun headingsLinksImages() {
        org.junit.Assert.assertEquals("<b>Noun</b><br>a house<br><br>x",
            Article.tidy("<h3>Noun</h3><a href=\"/w\">a house</a><img src=x><br><br><br>x"))
    }
}

class KindTest {
    @Test fun kinds() {
        org.junit.Assert.assertEquals(io.github.kafeg.inkpeek.ai.Kind.WORD, io.github.kafeg.inkpeek.ai.Kind.of("Haus"))
        org.junit.Assert.assertEquals(io.github.kafeg.inkpeek.ai.Kind.PHRASE, io.github.kafeg.inkpeek.ai.Kind.of("Was ist mit mir geschehen?"))
        org.junit.Assert.assertEquals(io.github.kafeg.inkpeek.ai.Kind.PASSAGE, io.github.kafeg.inkpeek.ai.Kind.of("Es war kein Traum. Sein Zimmer lag ruhig."))
        org.junit.Assert.assertEquals(io.github.kafeg.inkpeek.ai.Kind.PASSAGE, io.github.kafeg.inkpeek.ai.Kind.of("eins zwei drei vier fünf sechs sieben acht neun"))
        // no spaces between words: Chinese / Japanese
        org.junit.Assert.assertEquals(io.github.kafeg.inkpeek.ai.Kind.WORD, io.github.kafeg.inkpeek.ai.Kind.of("图书馆"))
        org.junit.Assert.assertEquals(io.github.kafeg.inkpeek.ai.Kind.PHRASE, io.github.kafeg.inkpeek.ai.Kind.of("我喜欢看书"))
        org.junit.Assert.assertEquals(io.github.kafeg.inkpeek.ai.Kind.PASSAGE, io.github.kafeg.inkpeek.ai.Kind.of("吾輩は猫である。名前はまだ無い。"))
    }
}

class PhraseTest {
    @Test fun phraseKeepsSentenceEnd() {
        assertEquals("Wo ist er?", cleanPhrase("„Wo ist er?“ "))
        assertEquals("Was ist „Heimat“?", cleanPhrase("Was ist „Heimat“?"))
        assertEquals("Er kam nicht…", cleanPhrase("— Er kam nicht…»"))
        assertEquals("sehr gut", cleanPhrase("(sehr gut),"))
        assertEquals("¿Dónde está?", cleanPhrase("«¿Dónde está?»"))
    }
}
