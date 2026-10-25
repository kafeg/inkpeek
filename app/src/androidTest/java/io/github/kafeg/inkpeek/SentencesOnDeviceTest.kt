package io.github.kafeg.inkpeek

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kafeg.inkpeek.mt.Engine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sentence breaks of offline translation (Android's ICU) on the device: tests/sentences/sentences.tsv, shared with LexiPeek. */
@RunWith(AndroidJUnit4::class)
class SentencesOnDeviceTest {
    @Test fun cases() {
        val cases = InstrumentationRegistry.getInstrumentation().context.assets.open("sentences.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        fun esc(s: String) = s.replace("\n", "\\n")
        val failed = cases.filter { c ->
            val (lang, text) = c
            val actual = Engine.sentences(text.replace("\\n", "\n"), lang).map(::esc)
            val ok = actual == c.drop(2)
            // A failing line prints what the device gives, as a line of the file.
            Log.i(PacksOnDeviceTest.TAG, "${if (ok) "PASS" else "FAIL"}\t$lang\t$text\t${actual.joinToString("\t")}")
            !ok
        }
        Log.i(PacksOnDeviceTest.TAG, "${cases.size - failed.size} of ${cases.size} sentence cases passed")
        assertTrue("${failed.size} of ${cases.size} failed (adb logcat -s ${PacksOnDeviceTest.TAG})", failed.isEmpty())
    }
}
