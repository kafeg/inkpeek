package io.github.kafeg.inkpeek.stardict

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A downloadable dictionary from a public source; [from] and [to] are two-letter codes where one exists. */
data class CatalogItem(val source: String, val from: String, val to: String, val url: String, val size: Long, val license: String)

/** A public StarDict source: its listing's address and a parser of that listing. */
class Source(val id: String, val title: String, val url: String, val parse: (String) -> List<CatalogItem>)

object Catalog {
    val SOURCES = listOf(
        Source("wikdict", "WikDict", "https://download.wikdict.com/dictionaries/stardict/", ::wikdict),
        Source("wiktionary", "Wiktionary (xxyzz)", "https://api.github.com/repos/xxyzz/wiktionary_stardict/releases/latest", ::wiktionary),
        Source("freedict", "FreeDict", "https://freedict.org/freedict-database.json", ::freedict))

    /** WikDict: the server's file index, "wikdict-<from>-<to>.zip" with a size like 4M or 552K. */
    fun wikdict(html: String): List<CatalogItem> =
        Regex("""href="(wikdict-([a-z]{2,3})-([a-z]{2,3})\.zip)".*?\s(\d+(?:\.\d+)?)([KMG])\s*$""", RegexOption.MULTILINE).findAll(html).map { m ->
            val (file, from, to, n, unit) = m.destructured
            val size = (n.toDouble() * when (unit) { "K" -> 1e3; "M" -> 1e6; else -> 1e9 }).toLong()
            CatalogItem("wikdict", from, to, SOURCES[0].url + file, size, "CC BY-SA 4.0")
        }.toList()

    /** xxyzz/wiktionary_stardict: assets "<words>-<glosses>.tar.zst". */
    fun wiktionary(json: String): List<CatalogItem> {
        val assets = JSONObject(json).getJSONArray("assets")
        return (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name").endsWith(".tar.zst") }
            .map { a ->
                val pair = a.getString("name").removeSuffix(".tar.zst").split("-", limit = 2)
                CatalogItem("wiktionary", pair[0], pair.getOrElse(1) { "?" }, a.getString("browser_download_url"), a.optLong("size"), "CC BY-SA 4.0")
            }
    }

    /** FreeDict database: "<src>-<dst>" in three-letter codes, each with a "stardict" release. */
    fun freedict(json: String): List<CatalogItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { d ->
            val rel = d.optJSONArray("releases") ?: return@mapNotNull null
            val sd = (0 until rel.length()).map { rel.getJSONObject(it) }.firstOrNull { it.optString("platform") == "stardict" }
                ?: return@mapNotNull null
            val pair = d.optString("name").split("-", limit = 2)
            CatalogItem("freedict", two(pair[0]), two(pair.getOrElse(1) { "?" }), sd.getString("URL"),
                sd.optString("size").toLongOrNull() ?: 0, "GPL / CC (see the dictionary)")
        }
    }

    private val TWO by lazy { Locale.getISOLanguages().associateBy { runCatching { Locale(it).isO3Language }.getOrDefault(it) } }

    /** "deu" → "de"; a code without a two-letter one stays. */
    fun two(code: String): String = TWO[code] ?: code
}
