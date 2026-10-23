package io.github.kafeg.inkpeek

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The notes store on the device's SQLite, in files of its own (the app's store is not touched). */
@RunWith(AndroidJUnit4::class)
class StoreOnDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fresh(name: String) = File(ctx.cacheDir, name).also { f -> listOf("", "-journal").forEach { File(f.path + it).delete() } }
        .let { Store.at(ctx, it.path) }

    @Test fun looksAreCountedAcrossForms() {
        val s = fresh("t-looks.db")
        s.seen("andere", "word"); s.base("andere", "word", "ander")
        s.seen("ander", "word"); s.base("ander", "word", "ander")
        s.seen("anderen", "word"); s.seen("anderen", "word"); s.base("anderen", "word", "ander")
        assertEquals(4, s.looks("ander"))
        // The base noted before the look is counted (the card's two jobs may finish in any order).
        s.base("Häusern", "word", "Haus"); s.seen("Häusern", "word")
        assertEquals(1, s.looks("Haus"))
        assertEquals(0, s.looks("gehen"))
        s.close()
    }

    @Test fun backupAndRestoreAddOnlyWhatIsMissing() {
        val a = fresh("t-a.db")
        a.save("ander", "word", "other", "en"); a.seen("ander", "word")
        a.putAnswer("ander", "en", "quick", 1, "m", "{}")
        val file = File(ctx.cacheDir, "t-backup.db").also { it.delete() }
        a.backup(file)
        assertTrue(file.length() > 0)
        val b = fresh("t-b.db")
        b.save("ander", "word", "kept here", "en"); b.save("Haus", "word", "house", "en")
        assertEquals(2, b.restore(file))   // the history row and the answer; "ander" is already saved here
        assertEquals("kept here", b.saved("word", "en").first { it.text == "ander" }.meaning)
        assertEquals("{}", b.answer("ander", "en", "quick", 1))
        assertEquals(0, b.restore(file))   // twice: nothing new
        a.close(); b.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun aFileThatIsNotABackupIsRefused() {
        val other = File(ctx.cacheDir, "t-other.db").also { it.delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(other, null).use { it.execSQL("CREATE TABLE x(a)") }
        fresh("t-c.db").restore(other)
    }
}
