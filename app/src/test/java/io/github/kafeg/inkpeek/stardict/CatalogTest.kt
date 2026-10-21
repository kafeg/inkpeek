package io.github.kafeg.inkpeek.stardict

import org.junit.Assert.assertEquals
import org.junit.Test

/** The three sources' listings, as they look today. */
class CatalogTest {
    @Test fun wikdict() {
        val html = """<a href="wikdict-de-ru.zip">wikdict-de-ru.zip</a>                                  23-Jun-2026 21:22      4M
<a href="wikdict-zh-sv.zip">wikdict-zh-sv.zip</a>                                  23-Jun-2026 21:22    639K
"""
        assertEquals(listOf(CatalogItem("wikdict", "de", "ru", "https://download.wikdict.com/dictionaries/stardict/wikdict-de-ru.zip", 4_000_000, "CC BY-SA 4.0"),
            CatalogItem("wikdict", "zh", "sv", "https://download.wikdict.com/dictionaries/stardict/wikdict-zh-sv.zip", 639_000, "CC BY-SA 4.0")), Catalog.wikdict(html))
    }

    @Test fun wiktionary() {
        val json = """{"tag_name":"20261020","assets":[{"name":"de-ru.tar.zst","size":8612661,"browser_download_url":"https://x/de-ru.tar.zst"},
            {"name":"checksums.txt","size":1,"browser_download_url":"https://x/c"}]}"""
        assertEquals(listOf(CatalogItem("wiktionary", "de", "ru", "https://x/de-ru.tar.zst", 8612661, "CC BY-SA 4.0")), Catalog.wiktionary(json))
    }

    @Test fun freedictInTwoLetterCodes() {
        val json = """[{"name":"deu-eng","releases":[{"URL":"https://x/src.tar.xz","platform":"src","size":"1"},
            {"URL":"https://x/freedict-deu-eng-1.9-fd1.stardict.tar.xz","platform":"stardict","size":"20967808"}]},{"name":"x-y","releases":[]}]"""
        assertEquals(listOf(CatalogItem("freedict", "de", "en", "https://x/freedict-deu-eng-1.9-fd1.stardict.tar.xz", 20967808, "GPL / CC (see the dictionary)")),
            Catalog.freedict(json))
        assertEquals("fr", Catalog.two("fra"))
        assertEquals("kha", Catalog.two("kha"))
    }
}
