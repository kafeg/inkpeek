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

    @Test fun aListLineIsTheNewestAiAnswer() {
        val s = fresh("t-newest.db")
        s.save("Haus", "word", "", "en")
        s.putAnswer("Haus", "en", "quick", 1, "m", """{"translation":"old"}"""); Thread.sleep(5)
        s.putAnswer("Haus", "en", "quick", 2, "m", """{"translation":"new"}""")
        assertEquals("new", s.saved("word", "en").single().meaning)
        s.seen("Haus", "word")
        assertEquals("new", s.history("en").single().meaning)
        s.close()
    }

    /** A damaged file is put aside, not deleted (Android's default): the notes in it may still be saved. */
    @Test fun withoutHistoryOnlySavedAnswersStay() {
        val s = fresh("t-unkept.db")
        s.save("Haus", "word", "", "en")
        s.putAnswer("Haus", "en", "quick", 1, "m", "kept"); s.putAnswer("geheim", "en", "quick", 1, "m", "gone")
        s.dropUnkept()
        assertEquals("kept", s.answer("Haus", "en", "quick", 1)); assertEquals(null, s.answer("geheim", "en", "quick", 1))
        assertEquals(0, s.count("history"))
    }

    @Test fun aDamagedFileIsPutAside() {
        val f = File(ctx.cacheDir, "t-bad.db")
        ctx.cacheDir.listFiles { x -> x.name.startsWith("t-bad.db") }?.forEach { it.delete() }
        f.writeText("not a database, but notes ".repeat(200))
        val s = Store.at(ctx, f.path)
        s.save("Haus", "word", "house", "en")
        assertEquals(1, s.saved("word", "en").size)
        assertTrue(ctx.cacheDir.list()!!.any { it.startsWith("t-bad.db.corrupt-") })
        s.close()
    }

    /** A copy a day, the last five kept; nothing written for an empty store or twice a day. */
    @Test fun dailyCopiesKeepTheLastFive() {
        val dir = File(ctx.cacheDir, "t-copies").also { it.deleteRecursively(); it.mkdirs() }
        val s = fresh("t-copies.db")
        s.autoBackup(dir); assertEquals(0, dir.list()!!.size)
        (1..6).forEach { File(dir, "inkpeek-2020-01-0$it.db").writeText("old") }
        s.save("Haus", "word", "house", "en")
        s.autoBackup(dir); s.autoBackup(dir)
        val left = dir.list()!!.sorted()
        assertEquals(5, left.size)
        assertTrue(left.last().startsWith("inkpeek-20") && left.last() > "inkpeek-2020-01-06.db")
        assertEquals(1, Store.at(ctx, File(dir, left.last()).path).saved("word", "en").size)
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

    @Test fun aWordSavedAsItsBaseFormKeepsItsAnswers() {
        val s = fresh("t-keep.db")
        s.seen("andere", "word"); s.base("andere", "word", "ander")
        s.putAnswer("andere", "en", "quick", 1, "m", """{"translation":"other"}""")
        s.save("ander", "word", "other", "en", from = "andere")
        s.putAnswer("andere", "en", "analysis", 1, "m", "{}")      // answered after it was saved
        s.clearHistory()
        assertEquals("""{"translation":"other"}""", s.answer("ander", "en", "quick", 1))
        assertEquals("{}", s.answer("ander", "en", "analysis", 1))
        s.close()
    }

    @Test fun anImportKeepsTheHistoryLimit() {
        val a = fresh("t-many.db")
        a.writableDatabase.run { beginTransaction(); try {
            for (i in 0 until Store.HISTORY) execSQL("INSERT INTO history(text, kind, seen, count) VALUES(?, 'word', ?, 1)", arrayOf("w$i", i))
            setTransactionSuccessful() } finally { endTransaction() } }
        val file = File(ctx.cacheDir, "t-many-backup.db").also { it.delete() }
        a.backup(file)
        val b = fresh("t-few.db")
        repeat(5) { b.seen("mine$it", "word") }
        b.restore(file)
        assertEquals(Store.HISTORY, b.count("history"))
        assertTrue(b.history("en").take(5).all { it.text.startsWith("mine") })   // the newest stay
        a.close(); b.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun aFileThatIsNotABackupIsRefused() {
        val other = File(ctx.cacheDir, "t-other.db").also { it.delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(other, null).use { it.execSQL("CREATE TABLE x(a)") }
        fresh("t-c.db").restore(other)
    }
}
