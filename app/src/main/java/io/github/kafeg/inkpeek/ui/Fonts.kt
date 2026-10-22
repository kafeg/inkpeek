package io.github.kafeg.inkpeek.ui

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Environment
import java.io.File

/** The card's font: Android's three, or a .ttf / .otf from the folders readers keep fonts in, or copied to InkPeek/fonts. */
object Fonts {
    /** Where fonts are looked for: Boox's own folder, KOReader's, InkPeek's (files chosen in the app). */
    val DIRS = listOf("fonts", "koreader/fonts", "InkPeek/fonts")
    private val STYLE = Regex("[-_ ]?(Bold|Italic|BoldItalic|Oblique|BoldOblique|Semi[Bb]old|Medium|Light|Thin|Black|Extra[Bb]old|Extra[Ll]ight|Heavy)$", RegexOption.IGNORE_CASE)
    private val cache = HashMap<String, Typeface?>()

    /** The font files found, one per family (its regular face: bold and italic files come with it). */
    fun found(): List<File> = DIRS.map { File(Environment.getExternalStorageDirectory(), it) }.filter { it.isDirectory }
        .flatMap { d -> d.walkTopDown().maxDepth(3).filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }.toList() }
        .filter { !STYLE.containsMatchIn(it.nameWithoutExtension) }.distinctBy { it.name }.sortedBy { it.nameWithoutExtension.lowercase() }

    /** The family's name for the list: "Literata-Regular" → "Literata". */
    fun name(f: File) = f.nameWithoutExtension.replace(Regex("[-_ ]?(Regular|Book|Roman)$", RegexOption.IGNORE_CASE), "")

    /** null = Android's own sans (nothing to set). A file that cannot be read falls back to it. */
    @Synchronized fun face(choice: String): Typeface? = when (choice) {
        "" -> null
        "serif" -> Typeface.SERIF
        "monospace" -> Typeface.MONOSPACE
        else -> cache.getOrPut(choice) { runCatching { family(File(choice)) }.getOrNull() }
    }

    /** The file with its bold and italic siblings, so <b> and <i> in articles use them; missing letters from Android's. */
    private fun family(f: File): Typeface {
        val base = name(f)
        val fam = FontFamily.Builder(Font.Builder(f).build())
        f.parentFile?.listFiles()?.filter { it != f && it.nameWithoutExtension.startsWith(base) && STYLE.containsMatchIn(it.nameWithoutExtension) }
            ?.forEach { runCatching { fam.addFont(Font.Builder(it).build()) } } // a second face of the same style is refused: skipped
        return Typeface.CustomFallbackBuilder(fam.build()).setSystemFallback("sans-serif").build()
    }

    /** A chosen file copied into InkPeek/fonts (it stays there when the original goes). */
    fun keep(home: File, name: String, input: java.io.InputStream): File =
        File(home, "fonts/" + File(name).name).also { it.parentFile?.mkdirs(); it.outputStream().use { o -> input.copyTo(o) } }
}
