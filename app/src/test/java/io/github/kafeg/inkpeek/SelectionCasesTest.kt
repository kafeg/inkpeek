package io.github.kafeg.inkpeek

import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.stardict.Library
import io.github.kafeg.inkpeek.stardict.normKey
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

/** The shared selection cases in tests/lookup (LexiPeek runs them too): kind, title, query and the words a card shows. */
class SelectionCasesTest {
    /** One own dictionary of [words], each article "<p class="head">word — x</p>". */
    private fun library(words: List<String>): Library {
        val root = Files.createTempDirectory("sel").toFile(); val dir = File(root, "own/d").apply { mkdirs() }
        val sorted = words.sortedBy(::normKey)
        val data = ByteArrayOutputStream(); val i = ByteArrayOutputStream(); val o = DataOutputStream(i)
        sorted.forEach { w ->
            val b = "<p class=\"head\">$w — x</p>".toByteArray()
            o.write(w.toByteArray()); o.write(0); o.writeInt(data.size()); o.writeInt(b.size); data.write(b)
        }
        File(dir, "d.idx").writeBytes(i.toByteArray()); File(dir, "d.dict").writeBytes(data.toByteArray())
        File(dir, "d.ifo").writeText("StarDict's dict ifo file\nversion=3.0.0\nbookname=d\nwordcount=${sorted.size}\nsametypesequence=h\n")
        return Library(File(root, "packs").apply { mkdirs() }, listOf(File(root, "own")), File(root, "work"))
            .also { lib -> lib.found().forEach { it.prepare() } }
    }

    @Test fun cases() {
        val failures = listOf("selection.tsv", "dashes.tsv").flatMap { f ->
            File("../tests/lookup/$f").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                val (known, text, kind, title, query, words) = line.split('\t').let { Six(it) }
                val lib = library(known.split('|').filter { it.isNotEmpty() })
                val c = Selection.open(text, lib, "ru")
                val shown = if (c.kind == Kind.WORD) lib.lookup(c.query, "ru").map { it.second.first().word }
                else lib.gloss(c.query, "ru").map { g -> g.word + if (g.head == null) "?" else "" }
                val got = listOf(c.kind.name.lowercase(), c.title, c.query, shown.joinToString("|"))
                if (got == listOf(kind, title, query, words)) null else "$f: $text → $got, expected ${listOf(kind, title, query, words)}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private class Six(val f: List<String>) {
        operator fun component1() = f[0]; operator fun component2() = f[1]; operator fun component3() = f[2]
        operator fun component4() = f[3]; operator fun component5() = f[4]; operator fun component6() = f.getOrElse(5) { "" }
    }
}
