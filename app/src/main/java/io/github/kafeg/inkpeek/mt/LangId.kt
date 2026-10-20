package io.github.kafeg.inkpeek.mt

import java.io.File

/** Language of a text by fastText's lid.176 model (from the packs): ISO codes with probabilities, best first. */
object LangId {
    private val native by lazy { System.loadLibrary("inkpeek_mt") }
    private external fun detectNative(model: String, text: String, k: Int, threshold: Float): String

    /** Blocking; the model loads once. */
    fun detect(model: File, text: String, k: Int = 3): List<Pair<String, Float>> = native.let {
        detectNative(model.path, text, k, 0.01f).lines().filter { it.isNotBlank() }.map { l ->
            l.substringBefore(' ') to (l.substringAfter(' ').toFloatOrNull() ?: 0f)
        }
    }
}
