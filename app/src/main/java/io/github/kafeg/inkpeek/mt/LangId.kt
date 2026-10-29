package io.github.kafeg.inkpeek.mt

import io.github.kafeg.inkpeek.packs.Packs
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.answer
import io.github.kafeg.inkpeek.stardict.book
import java.io.File

/** Language of a text by fastText's lid.176 model (from the packs): ISO codes with probabilities, best first. */
object LangId {
    private val native by lazy { System.loadLibrary("inkpeek_mt") }
    private external fun detectNative(model: String, text: ByteArray, k: Int, threshold: Float): String

    /** Blocking; the model loads once. */
    fun detect(model: File, text: String, k: Int = 3): List<Pair<String, Float>> = native.let {
        detectNative(model.path, text.toByteArray(Charsets.UTF_8), k, 0.01f).lines().filter { it.isNotBlank() }.map { l ->
            l.substringBefore(' ') to (l.substringAfter(' ').toFloatOrNull() ?: 0f)
        }
    }

    /**
     * Language of [text] among the installed book languages for [answer]: fastText; when it is unsure, the pack
     * dictionary that knows more of the words. Returns (book language, null) or (null, a language without a pack).
     * Blocking. [self]: the answer language is a candidate too (languages you learn), told by its packs into others.
     */
    fun source(text: String, answer: String, packs: Packs, dicts: List<StarDict>, self: Boolean = false): Pair<String?, String?> {
        // The book languages of the ticked pairs' dictionaries into this answer language.
        val books = dicts.filter { it.answer == answer }.mapNotNull { it.book }.distinct() +
            if (self && dicts.any { it.book == answer }) listOf(answer) else emptyList()
        if (books.isEmpty()) return null to null
        val guess = runCatching { if (packs.langid.exists()) detect(packs.langid, text, 5) else emptyList() }.getOrDefault(emptyList())
        val top = guess.firstOrNull()
        // When fastText is unsure (a word or two): how many words each book language's dictionary knows, forms included.
        val words = Regex("[\\p{L}][\\p{L}'’-]*").findAll(text).map { it.value }.distinct().take(40).toList()
        val known = books.associateWith { x ->
            val d = dicts.firstOrNull { it.book == x && (it.answer == answer || x == answer) }
            if (d == null || !d.ready) 0 else words.count { w -> runCatching { d.lookup(w).isNotEmpty() }.getOrDefault(false) }
        }
        android.util.Log.i("InkPeek", "language: fastText $guess, dictionaries know $known of ${words.size}")
        val best = known.maxByOrNull { it.value }
        return when {
            top != null && top.second >= 0.5f && top.first in books -> top.first to null
            // fastText is sure it is a language without a pack: say so, unless a book dictionary knows nearly every word.
            top != null && top.second >= 0.5f && (best == null || best.value < words.size * 0.8) -> null to top.first
            best != null && best.value > 0 -> best.key to null
            top != null && top.first in books -> top.first to null
            // No sign of any pack's language: not a guess (fastText's best, if any, is named).
            else -> null to top?.first
        }
    }
}
