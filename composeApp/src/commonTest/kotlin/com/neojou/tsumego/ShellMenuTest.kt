package com.neojou.tsumego

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellMenuTest {
    @Test
    fun fileMenuIsOpenAndSaveOnly() {
        val items = fileMenuItems(saveEnabled = true, onOpen = {}, onSave = {})
        assertEquals(listOf("Open", "Save"), items.map { it.label })
        val nested = items.flatMap { it.children }.map { it.label }
        assertTrue("角上做活" !in nested)
        assertTrue("小殺棋" !in nested)
        assertTrue("牆與真盤邊" !in nested)
        assertTrue("Samples" !in items.map { it.label })
        assertTrue("Samples" !in nested)
    }
}
