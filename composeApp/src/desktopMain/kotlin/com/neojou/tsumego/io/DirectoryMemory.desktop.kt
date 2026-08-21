package com.neojou.tsumego.io

import java.util.prefs.Preferences

actual fun platformDirectoryMemory(): DirectoryMemory = PrefsDirectoryMemory

private object PrefsDirectoryMemory : DirectoryMemory {
    private val prefs: Preferences = Preferences.userRoot().node("com/neojou/tsumego")

    override fun lastDirectory(): String? =
        prefs.get("lastProblemDir", null)?.takeIf { it.isNotBlank() }

    override fun rememberFile(path: String) {
        prefs.put("lastProblemDir", directoryOf(path))
        prefs.flush()
    }
}
