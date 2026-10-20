package io.github.kafeg.inkpeek

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kafeg.inkpeek.mt.Engine
import io.github.kafeg.inkpeek.mt.LangId
import io.github.kafeg.inkpeek.stardict.headLine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The installed language packs through the app's own code, on the device: `scripts/device-test.sh`. Cases: assets/cases.tsv. */
@RunWith(AndroidJUnit4::class)
class PacksOnDeviceTest {
    private val prefs = Prefs(InstrumentationRegistry.getInstrumentation().targetContext)
    private val lib = prefs.library()

    @Test fun cases() {
        val cases = InstrumentationRegistry.getInstrumentation().context.assets.open("cases.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        lib.all().filter { !it.ready }.forEach { it.prepare() }
        val failed = cases.filter { (kind, pair, input, expected) ->
            val t0 = System.currentTimeMillis()
            val actual = runCatching { run(kind, pair, input) }.getOrElse { "ERROR ${it.javaClass.simpleName}: ${it.message}" }
            val ok = expected.split(" | ").any { e -> when (kind) {
                "head" -> actual.startsWith(e)
                "lang" -> actual == e
                else -> actual.contains(e, ignoreCase = true)
            } }
            Log.i(TAG, "${if (ok) "PASS" else "FAIL"}\t$kind\t$pair\t$input\t$expected\t$actual\t${System.currentTimeMillis() - t0} ms")
            !ok
        }
        Log.i(TAG, "${cases.size - failed.size} of ${cases.size} passed")
        assertTrue("${failed.size} of ${cases.size} failed (adb logcat -s $TAG)", failed.isEmpty())
    }

    private fun run(kind: String, pair: String, input: String): String = when (kind) {
        // As the card does: the whole library (forms → base across dictionaries), then this pair's dictionary.
        "head", "mean" -> lib.lookup(input, emptyList(), emptySet()).firstOrNull { (d, _) -> d.ifo.parentFile?.name == "dict-$pair" }
            ?.second?.firstNotNullOfOrNull { headLine(it.html) }?.let { if (kind == "head") it.substringBefore(" — ") else it.substringAfter(" — ") } ?: "(not found)"
        "lang" -> LangId.source(input, pair, prefs.packs, lib).let { (book, other) -> book ?: "!$other" }
        "tr" -> pair.split('-').let { (x, y) -> Engine.translate(prefs.modelsDir, x, y, input, keep = true) }
        else -> error("Unknown case kind $kind")
    }

    companion object { const val TAG = "InkPeekTest" }
}
