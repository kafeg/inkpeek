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
    private fun dict(dir: File, gz: Boolean = true): File {
        val words = listOf("Haus" to "<b>Haus</b> n — дом", "abend" to "вечер", "Ärger" to "досада")
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
        val syn = ByteArrayOutputStream(); DataOutputStream(syn).use { it.write("Häuser".toByteArray()); it.write(0); it.writeInt(0) }
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

class GlossTest {
    @Test fun firstMeaning() {
        org.junit.Assert.assertEquals("дом, здание", shortMeaning("<b>Haus</b><br>IPA: [haʊ̯s]<br><ol><li>дом, здание</li><li>семья</li></ol>", "Haus"))
        org.junit.Assert.assertEquals("house, building", shortMeaning("Haus<br>• house, building<br>• home", "Haus"))
        org.junit.Assert.assertEquals("house, building", shortMeaning("<b>Haus</b><br><b>Noun</b><ul><li>IPA: [haʊ̯s]</li></ul>Haus n<ol><li>house, building</li></ol>", "Haus"))
        // Russian Wiktionary: transcription list first, the sense with an example after ◆
        org.junit.Assert.assertEquals("когда, в то время как", shortMeaning("<b>als</b><ul class=\"transcription\"><li>МФА: [als]</li></ul><b>Значение</b><ol><li><i></i> когда, в то время как <span>◆ Als er kam…</span></li></ol>", "als"))
    }

    @Test fun wikdictArticle() {
        val gehen = "<div>/<font color=\"gray\">ɡeːn</font>/<br> <div><font class=\"grammar\" color=\"green\">verb</font></div><ol><li><ol>" +
            "<li><div>ходить</div></li><li><div>уходить</div></li><li><div>годиться</div></li><li><div>отойти</div></li></ol></li></ol></div>"
        assertEquals("ходить, уходить, годиться", shortMeaning(gehen, "gehen"))
        val gewitter = "<div>/<font color=\"gray\">ɡəˈvɪtɐ</font>/<br> <div><font class=\"grammar\" color=\"green\">noun, neutral</font></div>" +
            "Wettererscheinung mit Blitz und Donner<div>гроза</div></div>"
        assertEquals("гроза", shortMeaning(gewitter, "Gewitter"))
        assertEquals("noun, neutral", grammarOf(gewitter))
        val go = "<div><font class=\"grammar\">noun</font></div><ol><li><div>очередь</div></li></ol>" +
            "<div><font class=\"grammar\">verb</font></div><ol><li><div>идти</div></li><li><div>ехать</div></li></ol>"
        assertEquals("очередь", shortMeaning(go, "go"))
        assertEquals("идти, ехать", shortMeaning(go, "go", "verb"))
        assertEquals("verb", grammarOf(go, "verb"))
        // Mueller
        assertEquals("от go 1", shortMeaning("[went] _p. от go 1", "went"))
        assertEquals("идти, ходить", shortMeaning("[gəu] 1. _v. (went - gone) 1&gt; идти, ходить; быть в движении", "go"))
    }

    @Test fun phraseKeepsSentenceEnd() {
        assertEquals("Wo ist er?", cleanPhrase("„Wo ist er?“ "))
        assertEquals("Was ist „Heimat“?", cleanPhrase("Was ist „Heimat“?"))
        assertEquals("Er kam nicht…", cleanPhrase("— Er kam nicht…»"))
        assertEquals("sehr gut", cleanPhrase("(sehr gut),"))
        assertEquals("¿Dónde está?", cleanPhrase("«¿Dónde está?»"))
    }
}
