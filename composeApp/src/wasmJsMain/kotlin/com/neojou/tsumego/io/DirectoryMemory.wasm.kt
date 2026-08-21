package com.neojou.tsumego.io

actual fun platformDirectoryMemory(): DirectoryMemory = WasmDirectoryMemory

private object WasmDirectoryMemory : DirectoryMemory {
    private val memory = InMemoryDirectoryMemory()

    override fun lastDirectory(): String? = memory.lastDirectory()

    override fun rememberFile(path: String) = memory.rememberFile(path)
}
