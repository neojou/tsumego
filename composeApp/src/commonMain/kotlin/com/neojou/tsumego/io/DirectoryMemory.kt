package com.neojou.tsumego.io

fun directoryOf(filePath: String): String {
    val slash = filePath.lastIndexOf('/')
    val back = filePath.lastIndexOf('\\')
    val i = maxOf(slash, back)
    return if (i <= 0) "." else filePath.substring(0, i)
}

interface DirectoryMemory {
    fun lastDirectory(): String?
    fun rememberFile(path: String)
}

class InMemoryDirectoryMemory : DirectoryMemory {
    private var directory: String? = null

    override fun lastDirectory(): String? = directory

    override fun rememberFile(path: String) {
        directory = directoryOf(path)
    }
}

expect fun platformDirectoryMemory(): DirectoryMemory
