package io.github.kafeg.inkpeek.ui

/** What a phrase opens on (Card → Opening): "offline", "ai" (translation), "dict" (word by word), "analysis", "context". */
object Opening {
    /** The choices these settings allow, in the list's order: word by word always. */
    fun choices(offline: Boolean, aiTranslation: Boolean, modules: Collection<String>): List<String> =
        listOfNotNull("offline".takeIf { offline }, "ai".takeIf { aiTranslation }, "dict", "analysis".takeIf { "analysis" in modules }, "context".takeIf { "context" in modules })

    /** The choices that ask AI: only these can be [pick]'s "blocked". */
    val AI = setOf("ai", "analysis", "context")

    /** [chosen] when it is a choice and, if an AI one, not [blocked] now (no network, no key); else offline, else word by word. */
    fun pick(chosen: String, choices: List<String>, blocked: (String) -> Boolean = { false }): String =
        if (chosen in choices && !(chosen in AI && blocked(chosen))) chosen else if ("offline" in choices) "offline" else "dict"
}
