package io.github.kafeg.inkpeek

import io.github.kafeg.inkpeek.ai.Ai

/** The process: AI reads its settings (picked parts, edited prompts, what a server accepts) from here, on every screen. */
class App : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs(this).let { p -> Ai.recall = { p.variant(it) }; Ai.remember = { k, v -> p.setVariant(k, v) }; Ai.edited = { p.prompt(it) }; Ai.picked = { p.aiKey(it) } }
    }
}
