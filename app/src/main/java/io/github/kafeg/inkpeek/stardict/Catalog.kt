package io.github.kafeg.inkpeek.stardict

import org.json.JSONArray
import org.json.JSONObject

/** A downloadable dictionary from a public source. */
data class CatalogItem(val source: String, val from: String, val to: String, val title: String, val url: String,
                       val size: Long, val license: String)

/** Public StarDict sources; each parser takes the source's own listing (JSON text). */
object Catalog {
    const val WIKTIONARY_URL = "https://api.github.com/repos/xxyzz/wiktionary_stardict/releases/latest"
    const val FREEDICT_URL = "https://freedict.org/freedict-database.json"
    const val WIKDICT_URL = "https://download.wikdict.com/dictionaries/stardict/"

    /** WikDict: the server's file index, "wikdict-<from>-<to>.zip" with a size like 4M or 552K. */
    fun wikdict(html: String): List<CatalogItem> =
        Regex("""href="(wikdict-([a-z]{2,3})-([a-z]{2,3})\.zip)".*?\s(\d+(?:\.\d+)?)([KMG])\s*$""", RegexOption.MULTILINE).findAll(html).map { m ->
            val (file, from, to, n, unit) = m.destructured
            val size = (n.toDouble() * when (unit) { "K" -> 1e3; "M" -> 1e6; else -> 1e9 }).toLong()
            CatalogItem("WikDict", from, to, "WikDict $from → $to", WIKDICT_URL + file, size, "CC BY-SA 4.0")
        }.toList()

    /** xxyzz/wiktionary_stardict: assets "<words>-<glosses>.tar.zst". */
    fun wiktionary(json: String): List<CatalogItem> {
        val assets = JSONObject(json).getJSONArray("assets")
        return (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.getString("name").endsWith(".tar.zst") }
            .map { a ->
                val pair = a.getString("name").removeSuffix(".tar.zst").split("-", limit = 2)
                CatalogItem("Wiktionary", pair[0], pair.getOrElse(1) { "?" }, "Wiktionary ${pair[0]} → ${pair.getOrElse(1) { "?" }}",
                    a.getString("browser_download_url"), a.optLong("size"), "CC BY-SA")
            }
    }

    /** FreeDict database: "<src>-<dst>" with a "stardict" release. */
    fun freedict(json: String): List<CatalogItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { d ->
            val name = d.optString("name"); val rel = d.optJSONArray("releases") ?: return@mapNotNull null
            val sd = (0 until rel.length()).map { rel.getJSONObject(it) }.firstOrNull { it.optString("platform") == "stardict" }
                ?: return@mapNotNull null
            val pair = name.split("-", limit = 2)
            CatalogItem("FreeDict", pair[0], pair.getOrElse(1) { "?" }, "FreeDict ${pair[0]} → ${pair.getOrElse(1) { "?" }}",
                sd.getString("URL"), sd.optString("size").toLongOrNull() ?: 0, "GPL/CC (see dictionary)")
        }
    }
}
