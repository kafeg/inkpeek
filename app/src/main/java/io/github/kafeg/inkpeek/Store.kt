package io.github.kafeg.inkpeek

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import java.io.File

/** One SQLite file in /sdcard/InkPeek (survives a reinstall): AI answers, saved words and phrases, lookup history. */
class Store private constructor(ctx: Context, path: String) : SQLiteOpenHelper(ctx, path, null, 1) {
    /** [kind]: "word" or "phrase"; [meaning]: one short line, may be empty. */
    data class Entry(val text: String, val kind: String, val meaning: String, val time: Long)

    init { setWriteAheadLoggingEnabled(false) } // shared storage: no -shm memory map

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE ai(text TEXT, lang TEXT, section TEXT, ver INTEGER, model TEXT, answer TEXT, created INTEGER, PRIMARY KEY(text, lang, section, ver))")
        db.execSQL("CREATE TABLE saved(text TEXT PRIMARY KEY, kind TEXT, meaning TEXT, created INTEGER)")
        db.execSQL("CREATE TABLE history(text TEXT PRIMARY KEY, kind TEXT, seen INTEGER, count INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, from: Int, to: Int) {}

    fun answer(text: String, lang: String, section: String, ver: Int): String? = readableDatabase.rawQuery(
        "SELECT answer FROM ai WHERE text=? AND lang=? AND section=? AND ver=?", arrayOf(text, lang, section, ver.toString())
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun putAnswer(text: String, lang: String, section: String, ver: Int, model: String, answer: String) {
        writableDatabase.insertWithOnConflict("ai", null, ContentValues().apply {
            put("text", text); put("lang", lang); put("section", section); put("ver", ver); put("model", model)
            put("answer", answer); put("created", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun answers(): Int = count("ai")
    fun clearAnswers() { writableDatabase.delete("ai", null, null) }

    fun isSaved(text: String) = readableDatabase.rawQuery("SELECT 1 FROM saved WHERE text=?", arrayOf(text)).use { it.moveToFirst() }
    fun save(text: String, kind: String, meaning: String) {
        writableDatabase.insertWithOnConflict("saved", null, ContentValues().apply {
            put("text", text); put("kind", kind); put("meaning", meaning); put("created", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun unsave(text: String) { writableDatabase.delete("saved", "text=?", arrayOf(text)) }

    /** Saved entries of [kind], newest first; an empty meaning is filled from a cached translation in [lang]. */
    fun saved(kind: String, lang: String): List<Entry> = list(
        "SELECT s.text, s.kind, s.meaning, s.created, a.answer FROM saved s LEFT JOIN ai a ON a.text=s.text AND a.lang=? AND a.section='quick' " +
            "WHERE s.kind=? GROUP BY s.text ORDER BY s.created DESC", arrayOf(lang, kind))

    fun seen(text: String, kind: String) {
        val db = writableDatabase
        // No UPSERT: Android 8 and 9 ship SQLite older than 3.24.
        db.execSQL("INSERT OR IGNORE INTO history(text, kind, seen, count) VALUES(?, ?, 0, 0)", arrayOf(text, kind))
        db.execSQL("UPDATE history SET seen=?, count=count+1 WHERE text=?", arrayOf(System.currentTimeMillis(), text))
        db.execSQL("DELETE FROM history WHERE text NOT IN (SELECT text FROM history ORDER BY seen DESC LIMIT $HISTORY)")
    }
    fun history(lang: String): List<Entry> = list(
        "SELECT h.text, h.kind, '', h.seen, a.answer FROM history h LEFT JOIN ai a ON a.text=h.text AND a.lang=? AND a.section='quick' " +
            "GROUP BY h.text ORDER BY h.seen DESC", arrayOf(lang))
    fun forget(text: String) { writableDatabase.delete("history", "text=?", arrayOf(text)) }
    fun clearHistory() { writableDatabase.delete("history", null, null) }
    fun count(table: String): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }
    fun count(table: String, kind: String): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM $table WHERE kind=?", arrayOf(kind)).use { it.moveToFirst(); it.getInt(0) }

    private fun list(sql: String, args: Array<String>) = readableDatabase.rawQuery(sql, args).use { c ->
        generateSequence { if (c.moveToNext()) Entry(c.getString(0), c.getString(1), c.getString(2).ifBlank { brief(c.getString(4)) }, c.getLong(3)) else null }.toList()
    }

    companion object {
        const val HISTORY = 1000
        @Volatile private var one: Store? = null

        /** On shared storage when the app may write there, else in the app's own files. */
        fun get(ctx: Context): Store = one ?: synchronized(this) {
            one ?: run {
                val dir = File(Environment.getExternalStorageDirectory(), "InkPeek")
                val path = if (Environment.isExternalStorageManager() && (dir.isDirectory || dir.mkdirs())) File(dir, "inkpeek.db").path
                    else ctx.getDatabasePath("inkpeek.db").path
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
