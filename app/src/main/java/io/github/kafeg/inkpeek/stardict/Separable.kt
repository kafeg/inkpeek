package io.github.kafeg.inkpeek.stardict

/**
 * Separable verbs written apart in a sentence: "Ich mache die Tür zu" → zumachen. Only the pack's data decides: a verb
 * is separable when its tables write it apart (.rel: its particle), and a finite form belongs to its base verb.
 * The particle closes its clause (a punctuation mark, the end, or a coordinating conjunction follows); the verb is the
 * nearest word before it that makes a separable verb with it; a capitalised word inside a clause is a noun.
 * Measured on de.wiktionary's examples: right in ≈ 95 % of the finds, ≈ 3 of 4 separated uses found.
 */
object Separable {
    data class Find(val form: String, val particle: String, val verb: String)

    private val TOKEN = Regex("""\p{L}+|[^\p{L}\p{N}\s]""")
    private val CONJ = setOf("und", "oder", "aber", "sondern", "denn")

    /** [bases]: the lemmas a word form belongs to; [particle]: a verb's particle when it is separable, else null. */
    fun find(text: String, bases: (String) -> Collection<String>, particle: (String) -> String?): List<Find> {
        val out = LinkedHashMap<String, Find>()
        for (c in clauses(text)) {
            for (j in 1 until c.size) {
                val p = c[j]
                if (p != p.lowercase() || !(j == c.size - 1 || c[j + 1] in CONJ)) continue
                for (i in j - 1 downTo 0) {
                    if (i > 0 && c[i] != c[i].lowercase()) continue
                    val verbs = bases(c[i].lowercase()).map { p + it }.filter { particle(it) == p }
                    if (verbs.isEmpty()) continue
                    verbs.forEach { out.putIfAbsent(it, Find(c[i], p, it)) }
                    break
                }
            }
        }
        return out.values.toList()
    }

    private fun clauses(text: String): List<List<String>> {
        val out = mutableListOf<List<String>>(); var cur = mutableListOf<String>()
        for (m in TOKEN.findAll(text)) {
            if (m.value[0].isLetter()) cur.add(m.value)
            else { if (cur.isNotEmpty()) out.add(cur); cur = mutableListOf() }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }
}
