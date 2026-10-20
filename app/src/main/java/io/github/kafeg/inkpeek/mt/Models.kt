package io.github.kafeg.inkpeek.mt

import java.io.File

/** Offline translation models on the device: one folder per direction, `<root>/<from><to>/` (from the language packs). */
object Models {
    fun dir(root: File, from: String, to: String) = File(root, from + to)

    /** Installed: the folder has its "set" file (the version and architecture, written last). */
    fun installed(root: File, from: String, to: String): Boolean = File(dir(root, from, to), "set").exists()
}
