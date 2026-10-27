package io.github.kafeg.inkpeek

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ui.CardActivity

/** The process: AI reads its settings (picked parts, edited prompts, what a server accepts) from here, on every screen. */
class App : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs(this).let { p -> Ai.recall = { p.variant(it) }; Ai.remember = { k, v -> p.setVariant(k, v) }; Ai.edited = { p.prompt(it) }; Ai.picked = { p.aiKey(it) } }
        registerActivityLifecycleCallbacks(Bars)
    }

    /** Android 15 draws screens under the status and navigation bars: keep their content out (and above the keyboard). */
    private object Bars : ActivityLifecycleCallbacks {
        override fun onActivityPostCreated(a: Activity, b: Bundle?) {
            if (a is CardActivity) return
            a.findViewById<View>(android.R.id.content).setOnApplyWindowInsetsListener { v, ins ->
                val bars = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = ins.getInsets(WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom)); ins
            }
        }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    }
}
