package io.github.kafeg.inkpeek

import io.github.kafeg.inkpeek.ai.Ask
import io.github.kafeg.inkpeek.mt.OfflineTranslation
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shared answer versions in tests/versions (LexiPeek runs them too): an answer saved by one app opens in the other. */
class VersionCasesTest {
    @Test fun versions() {
        val a = JSONObject(File("../tests/versions/versions.json").readText()).getJSONArray("cases")
        val failures = (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { c ->
            val got = if (c.has("ask")) Ask.valueOf(c.getString("ask").uppercase()).version(c.optString("study").takeIf { !c.isNull("study") })
            else c.getJSONArray("offline").let { s -> OfflineTranslation.version((0 until s.length()).map(s::getString)) }
            if (got == c.getInt("ver")) null else "$c ≠ $got"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
