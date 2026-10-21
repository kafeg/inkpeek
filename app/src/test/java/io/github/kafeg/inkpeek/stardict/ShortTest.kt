package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Test

/** The first meaning of other dictionaries' articles: Wiktionary, WikDict, Mueller. */
class ShortTest {
    @Test fun firstMeaning() {
        assertEquals("дом, здание", shortMeaning("<b>Haus</b><br>IPA: [haʊ̯s]<br><ol><li>дом, здание</li><li>семья</li></ol>", "Haus"))
        assertEquals("house, building", shortMeaning("Haus<br>• house, building<br>• home", "Haus"))
        assertEquals("house, building", shortMeaning("<b>Haus</b><br><b>Noun</b><ul><li>IPA: [haʊ̯s]</li></ul>Haus n<ol><li>house, building</li></ol>", "Haus"))
        // Russian Wiktionary: transcription list first, the sense with an example after ◆
        assertEquals("когда, в то время как", shortMeaning("<b>als</b><ul class=\"transcription\"><li>МФА: [als]</li></ul><b>Значение</b><ol><li><i></i> когда, в то время как <span>◆ Als er kam…</span></li></ol>", "als"))
    }

    @Test fun wikdictArticle() {
        val gehen = "<div>/<font color=\"gray\">ɡeːn</font>/<br> <div><font class=\"grammar\" color=\"green\">verb</font></div><ol><li><ol>" +
            "<li><div>ходить</div></li><li><div>уходить</div></li><li><div>годиться</div></li><li><div>отойти</div></li></ol></li></ol></div>"
        assertEquals("ходить, уходить, годиться", shortMeaning(gehen, "gehen"))
        val gewitter = "<div>/<font color=\"gray\">ɡəˈvɪtɐ</font>/<br> <div><font class=\"grammar\" color=\"green\">noun, neutral</font></div>" +
            "Wettererscheinung mit Blitz und Donner<div>гроза</div></div>"
        assertEquals("гроза", shortMeaning(gewitter, "Gewitter"))
        val go = "<div><font class=\"grammar\">noun</font></div><ol><li><div>очередь</div></li></ol>" +
            "<div><font class=\"grammar\">verb</font></div><ol><li><div>идти</div></li><li><div>ехать</div></li></ol>"
        assertEquals("очередь", shortMeaning(go, "go"))
        assertEquals("идти, ехать", shortMeaning(go, "go", "verb"))
        // Mueller
        assertEquals("от go 1", shortMeaning("[went] _p. от go 1", "went"))
        assertEquals("идти, ходить", shortMeaning("[gəu] 1. _v. (went - gone) 1&gt; идти, ходить; быть в движении", "go"))
    }
}
