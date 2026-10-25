package io.github.kafeg.inkpeek.mt

import android.app.ActivityManager
import android.content.Context
import io.github.kafeg.inkpeek.packs.Component
import java.io.File

/** What this device can run: its memory (as Android reports it: a "6 GB" phone shows ~5.5) and its CPU features. */
class Device(val totalMb: Int, val features: Set<String>) {
    /** Why [c] cannot run here, or null when it can. */
    fun cannot(c: Component): Cannot? = when {
        totalMb < c.ramMb -> Cannot.Memory(c.ramMb, totalMb)
        c.cpu.any { it !in features } -> Cannot.Cpu(c.cpu.filter { it !in features })
        else -> null
    }

    sealed class Cannot { class Memory(val needMb: Int, val haveMb: Int) : Cannot(); class Cpu(val missing: List<String>) : Cannot() }

    companion object {
        fun of(ctx: Context): Device {
            val m = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            // "Features : fp asimd … asimddp": dot product (asimddp) is what makes int8 LLM inference usable.
            val f = runCatching { File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") } }.getOrNull()
                ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.toSet().orEmpty()
            return Device((m.totalMem shr 20).toInt(), f)
        }
    }
}
