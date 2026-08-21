package com.neojou.tsumego.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DirectoryMemoryTest {
    @Test
    fun directoryOfUsesTheParentOfTheFile() {
        assertEquals("/Users/me/go", directoryOf("/Users/me/go/small_trick.tsumego.json"))
        assertEquals("C:\\puzzles", directoryOf("C:\\puzzles\\a.tsumego.json"))
        assertEquals(".", directoryOf("problem.tsumego.json"))
    }

    @Test
    fun rememberingAFileKeepsItsDirectoryForTheNextOpen() {
        val memory = InMemoryDirectoryMemory()
        assertNull(memory.lastDirectory())
        memory.rememberFile("/Users/me/go/small_trick.tsumego.json")
        assertEquals("/Users/me/go", memory.lastDirectory())
        memory.rememberFile("/tmp/more/b.tsumego.json")
        assertEquals("/tmp/more", memory.lastDirectory())
    }
}
