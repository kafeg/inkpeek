package io.github.kafeg.inkpeek.ui

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Environment
import java.io.File

/** The card's font: Android's, or a .ttf / .otf from the device's fonts, the folders readers keep fonts in, or InkPeek's. */
object Fonts {
    /** Where people's fonts are looked for: Boox's own folder, KOReader's, InkPeek's (files chosen in the app). */
    val DIRS = listOf("fonts", "koreader/fonts", "InkPeek/fonts")
    /** Letters the card's languages need: German, French, Polish, Russian. */
    const val LETTERS = "Haäßéłдж"
    private val STYLE = "(Bold|Italic|BoldItalic|Oblique|BoldOblique|Semi[Bb]old|Medium|Light|Thin|Black|Extra[Bb]old|Extra[Ll]ight|Heavy)"
    private val STYLED = Regex("[-_ ]?$STYLE$", RegexOption.IGNORE_CASE)
    private val cache = HashMap<String, Typeface?>()

    /** A font of the list: [id] is what the card keeps ("" = Android's, or a file's path). */
    data class Item(val id: String, val name: String, val where: String, val missing: String)

    private fun fontFiles(dir: File, depth: Int) = dir.walkTopDown().maxDepth(depth).filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
        .filter { !STYLED.containsMatchIn(it.nameWithoutExtension) }.toList()

    /** Android's own families that have every letter the card needs (most of them are for other scripts). */
    fun system(): List<Item> = systemList ?: fontFiles(File("/system/fonts"), 1)
        .distinctBy { name(it) }.filter { missing(it).isEmpty() }.sortedBy { name(it) }.map { Item(it.path, name(it), "Android", "") }
        .also { systemList = it }
    /** Android's fonts do not change while the app runs: read once. */
    @Volatile private var systemList: List<Item>? = null

    /** People's fonts, one per family; those without some letters say which. */
    fun yours(): List<Item> = DIRS.map { it to File(Environment.getExternalStorageDirectory(), it) }.filter { it.second.isDirectory }
        .flatMap { (where, d) -> fontFiles(d, 3).map { Item(it.path, name(it), where, missing(it)) } }.sortedBy { it.name.lowercase() }

    /** "Literata-Regular" → "Literata", "NotoSerif" → "Noto Serif". */
    fun name(f: File) = f.nameWithoutExtension.replace(Regex("[-_ ]?(Regular|Book|Roman)$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

    /** The letters of [LETTERS] the font itself has not, read from its character map (Android would stand in for them). */
    fun missing(f: File): String = runCatching { cmap(f).let { has -> LETTERS.filter { !has(it.code) } } }.getOrDefault(LETTERS)

    /** The font file's character map (TrueType / OpenType "cmap", Unicode subtable, format 4 or 12) as a test. */
    private fun cmap(f: File): (Int) -> Boolean = java.io.RandomAccessFile(f, "r").use { raf ->
        fun u16(at: Long): Int { raf.seek(at); return raf.readUnsignedShort() }
        fun u32(at: Long): Long { raf.seek(at); return raf.readInt().toLong() and 0xFFFFFFFFL }
        val tables = u16(4)
        val cmap = (0 until tables).map { 12L + 16 * it }.first { raf.seek(it); String(ByteArray(4).also { b -> raf.readFully(b) }) == "cmap" }.let { u32(it + 8) }
        val subs = (0 until u16(cmap + 2)).map { i -> val r = cmap + 4 + 8 * i; Triple(u16(r), u16(r + 2), cmap + u32(r + 4)) }
        // Full Unicode (format 12) first, else the BMP one (format 4).
        val sub = subs.firstOrNull { (p, e, at) -> (p == 3 && e == 10 || p == 0) && u16(at) == 12 }?.third
            ?: subs.first { (p, e, at) -> (p == 3 && e == 1 || p == 0) && u16(at) == 4 }.third
        val len = if (u16(sub) == 12) u32(sub + 4).toInt() else u16(sub + 2)
        val b = java.nio.ByteBuffer.wrap(ByteArray(len).also { raf.seek(sub); raf.readFully(it) })
        if (b.getShort(0).toInt() == 12) {
            val n = b.getInt(12)
            return@use { c -> (0 until n).any { i -> c >= b.getInt(16 + 12 * i) && c <= b.getInt(20 + 12 * i) } }
        }
        val seg = (b.getShort(6).toInt() and 0xFFFF) / 2
        val ends = 14; val starts = ends + 2 * seg + 2; val deltas = starts + 2 * seg; val ranges = deltas + 2 * seg
        fun at(o: Int) = b.getShort(o).toInt() and 0xFFFF
        return@use { c -> (0 until seg).firstOrNull { at(ends + 2 * it) >= c }?.let { i ->
            val start = at(starts + 2 * i); if (c < start) return@let false
            val range = at(ranges + 2 * i)
            val glyph = if (range == 0) c + at(deltas + 2 * i) else at(ranges + 2 * i + range + 2 * (c - start)).let { g -> if (g == 0) 0 else g + at(deltas + 2 * i) }
            glyph and 0xFFFF != 0
        } ?: false }
    }

    /** null = Android's own font (nothing to set). A file that cannot be read falls back to it. */
    @Synchronized fun face(choice: String): Typeface? = when (choice) {
        "" -> null
        else -> cache.getOrPut(choice) { runCatching { family(File(choice)) }.getOrNull() }
    }

    /** The file with its own bold and italic files ("X-Bold", not "XCondensed-Bold"); missing letters from Android's font. */
    private fun family(f: File): Typeface {
        val base = f.nameWithoutExtension.replace(Regex("[-_ ]?(Regular|Book|Roman)$", RegexOption.IGNORE_CASE), "")
        val own = Regex("^" + Regex.escape(base) + "[-_ ]?$STYLE$", RegexOption.IGNORE_CASE)
        val fam = FontFamily.Builder(Font.Builder(f).build())
        f.parentFile?.listFiles()?.filter { it != f && own.matches(it.nameWithoutExtension) }
            ?.forEach { runCatching { fam.addFont(Font.Builder(it).build()) } } // a second face of the same style is refused: skipped
        return Typeface.CustomFallbackBuilder(fam.build()).setSystemFallback("sans-serif").build()
    }

    /** A chosen file copied into InkPeek/fonts (it stays there when the original goes). */
    fun keep(home: File, name: String, input: java.io.InputStream): File =
        File(home, "fonts/" + File(name).name).also { it.parentFile?.mkdirs(); it.outputStream().use { o -> input.copyTo(o) } }

    /** What the settings show for the font in use. */
    fun label(choice: String, android: String): String = when (choice) {
        "" -> android
        else -> name(File(choice))
    }
}
