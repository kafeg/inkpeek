package io.github.kafeg.inkpeek

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import java.io.File

/**
 * One SQLite file in /sdcard/InkPeek (survives a reinstall): lookup history, saved words and phrases, and the answers
 * (AI, offline translation) of those texts: an answer lives as long as its text is in the history or saved.
 */
class Store private constructor(ctx: Context, path: String) : SQLiteOpenHelper(ctx, path, null, 3) {
    /** [kind]: "word" or "phrase"; [meaning]: one short line, may be empty. */
    data class Entry(val text: String, val kind: String, val meaning: String, val time: Long)

    init { setWriteAheadLoggingEnabled(false) } // shared storage: no -shm memory map

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE ai(text TEXT, lang TEXT, section TEXT, ver INTEGER, model TEXT, answer TEXT, created INTEGER, PRIMARY KEY(text, lang, section, ver))")
        db.execSQL("CREATE TABLE saved(text TEXT PRIMARY KEY, kind TEXT, meaning TEXT, created INTEGER, lang TEXT)")
        db.execSQL("CREATE TABLE history(text TEXT PRIMARY KEY, kind TEXT, seen INTEGER, count INTEGER, meaning TEXT, lang TEXT)")
    }

    /** An older app over a newer file (debug ↔ release share it): keep the data, the extra columns do no harm. */
    override fun onDowngrade(db: SQLiteDatabase, from: Int, to: Int) {}

    override fun onUpgrade(db: SQLiteDatabase, from: Int, to: Int) {
        // Only what is missing: an older app opening the file marks it with its own (lower) version but keeps the columns.
        if (from < 2) { add(db, "history", "meaning"); dropAnswers(db) }
        // The language of a meaning: a list in another app language shows its own when there is one.
        if (from < 3) { add(db, "saved", "lang"); add(db, "history", "lang") }
    }

    private fun add(db: SQLiteDatabase, table: String, column: String) {
        val has = db.rawQuery("PRAGMA table_info($table)", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(1) else null }.any { it == column } }
        if (!has) db.execSQL("ALTER TABLE $table ADD COLUMN $column TEXT")
    }

    /** Answers of texts that are neither in the history nor saved. */
    private fun dropAnswers(db: SQLiteDatabase) =
        db.execSQL("DELETE FROM ai WHERE text NOT IN (SELECT text FROM history) AND text NOT IN (SELECT text FROM saved)")

    fun answer(text: String, lang: String, section: String, ver: Int): String? = readableDatabase.rawQuery(
        "SELECT answer FROM ai WHERE text=? AND lang=? AND section=? AND ver=?", arrayOf(text, lang, section, ver.toString())
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun putAnswer(text: String, lang: String, section: String, ver: Int, model: String, answer: String) {
        writableDatabase.insertWithOnConflict("ai", null, ContentValues().apply {
            put("text", text); put("lang", lang); put("section", section); put("ver", ver); put("model", model)
            put("answer", answer); put("created", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isSaved(text: String) = readableDatabase.rawQuery("SELECT 1 FROM saved WHERE text=?", arrayOf(text)).use { it.moveToFirst() }
    fun save(text: String, kind: String, meaning: String, lang: String) {
        writableDatabase.insertWithOnConflict("saved", null, ContentValues().apply {
            put("text", text); put("kind", kind); put("meaning", meaning); put("lang", lang); put("created", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun unsave(text: String) { writableDatabase.run { delete("saved", "text=?", arrayOf(text)); dropAnswers(this) } }

    /** Saved entries of [kind], newest first, each with its line in [lang] where there is one (see [list]). */
    fun saved(kind: String, lang: String): List<Entry> = list(lang,
        "SELECT s.text, s.kind, s.meaning, s.created, a.answer, h.meaning, s.lang, h.lang FROM saved s LEFT JOIN history h ON h.text=s.text " +
            "LEFT JOIN ai a ON a.text=s.text AND a.lang=? AND a.section='quick' WHERE s.kind=? GROUP BY s.text ORDER BY s.created DESC", arrayOf(lang, kind))

    fun seen(text: String, kind: String) = writableDatabase.let { db -> db.beginTransaction(); try { seen(db, text, kind); db.setTransactionSuccessful() } finally { db.endTransaction() } }

    private fun seen(db: SQLiteDatabase, text: String, kind: String) {
        // No UPSERT: Android 8 and 9 ship SQLite older than 3.24.
        db.execSQL("INSERT OR IGNORE INTO history(text, kind, seen, count) VALUES(?, ?, 0, 0)", arrayOf(text, kind))
        db.execSQL("UPDATE history SET seen=?, count=count+1 WHERE text=?", arrayOf(System.currentTimeMillis(), text))
        if (android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM history", null) > HISTORY) {
            db.execSQL("DELETE FROM history WHERE text NOT IN (SELECT text FROM history ORDER BY seen DESC LIMIT $HISTORY)")
            dropAnswers(db)
        }
    }
    /** One short line for the history: the dictionary's first line of a word, the offline translation of a phrase. */
    fun meaning(text: String, kind: String, meaning: String, lang: String) {
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO history(text, kind, seen, count) VALUES(?, ?, ?, 0)", arrayOf(text, kind, System.currentTimeMillis()))
        db.execSQL("UPDATE history SET meaning=?, lang=? WHERE text=?", arrayOf(meaning, lang, text))
    }
    fun history(lang: String): List<Entry> = list(lang,
        "SELECT h.text, h.kind, '', h.seen, a.answer, h.meaning, '', h.lang FROM history h LEFT JOIN ai a ON a.text=h.text AND a.lang=? AND a.section='quick' " +
            "GROUP BY h.text ORDER BY h.seen DESC", arrayOf(lang))
    /** Removing a text from the history also removes its answers, unless it is saved. */
    fun forget(text: String) { writableDatabase.run { delete("history", "text=?", arrayOf(text)); dropAnswers(this) } }
    fun clearHistory() { writableDatabase.run { delete("history", null, null); dropAnswers(this) } }
    fun count(table: String): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }
    fun count(table: String, kind: String): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM $table WHERE kind=?", arrayOf(kind)).use { it.moveToFirst(); it.getInt(0) }

    /**
     * Rows: text, kind, saved line, time, AI answer in [lang], history line, saved line's language, history line's.
     * The line shown: one in [lang] (saved, history, the AI answer), else whichever there is (a list in a new app language).
     */
    private fun list(lang: String, sql: String, args: Array<String>) = readableDatabase.rawQuery(sql, args).use { c ->
        generateSequence { if (!c.moveToNext()) null else {
            val saved = c.getString(2).orEmpty(); val hist = c.getString(5).orEmpty(); val ai = brief(c.getString(4))
            val line = listOf(saved.takeIf { c.getString(6) == lang }, hist.takeIf { c.getString(7) == lang }, ai, saved, hist)
                .firstOrNull { !it.isNullOrBlank() }.orEmpty()
            Entry(c.getString(0), c.getString(1), line, c.getLong(3))
        } }.toList()
    }

    companion object {
        const val HISTORY = 1000
        @Volatile private var one: Store? = null

        /** On shared storage when the app may write there, else in the app's own files. */
        fun get(ctx: Context): Store = one ?: synchronized(this) {
            one ?: run {
                val dir = File(Environment.getExternalStorageDirectory(), "InkPeek")
                val own = ctx.getDatabasePath("inkpeek.db")
                val shared = File(dir, "inkpeek.db")
                val path = if (Environment.isExternalStorageManager() && (dir.isDirectory || dir.mkdirs())) {
                    // Used before all-files access was granted: the file moves to shared storage once.
                    if (!shared.exists() && own.exists()) runCatching { own.copyTo(shared) }.onSuccess { own.delete() }
                    shared.path
                } else own.path
                Store(ctx.applicationContext, path).also { one = it }
            }
        }

        /** One line from a quick AI answer (JSON): the translation, or the base form with its meanings. */
        fun brief(answer: String?): String = runCatching {
            val j = org.json.JSONObject(answer ?: return "")
            j.optString("translation").ifBlank {
                val m = j.optJSONArray("meanings")?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } }.orEmpty()
                listOf(listOf(j.optString("article"), j.optString("base")).filter { it.isNotBlank() }.joinToString(" "), m)
                    .filter { it.isNotBlank() }.joinToString(" — ")
            }.take(140)
        }.getOrDefault("")
    }
}
