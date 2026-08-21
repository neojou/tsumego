package com.neojou.tsumego

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellMenuTest {
    @Test
    fun topMenusAreInputFileEditAbout() {
        val items = shellMenuBarItems(
            saveEnabled = false,
            editEnabled = false,
            onBlackFirst = {},
            onWhiteFirst = {},
            onOpen = {},
            onSave = {},
            onEdit = {},
            onAbout = {},
        )
        assertEquals(listOf("Input", "File", "Edit", "About"), items.map { it.label })
        assertTrue(items.single { it.label == "Edit" }.enabled == false)
    }

    @Test
    fun editIsEnabledWhenAProblemIsOpen() {
        val items = shellMenuBarItems(
            saveEnabled = true,
            editEnabled = true,
            onBlackFirst = {},
            onWhiteFirst = {},
            onOpen = {},
            onSave = {},
            onEdit = {},
            onAbout = {},
        )
        assertTrue(items.single { it.label == "Edit" }.enabled)
    }

    @Test
    fun fileMenuListsKillSamplesOnly() {
        val items = fileMenuItems(
            saveEnabled = true,
            onOpen = {},
            onSave = {},
            sampleItems = listOf(
                com.neojou.tools.ui.menu.MyTopMenuItem(id = "sample-small-kill", label = "小殺棋", onClick = {}),
                com.neojou.tools.ui.menu.MyTopMenuItem(id = "sample-wall", label = "牆與真盤邊", onClick = {}),
            ),
        )
        assertEquals(listOf("Open", "Save", "Samples"), items.map { it.label })
        val nested = items.flatMap { it.children }.map { it.label }
        assertTrue("角上做活" !in nested)
        assertTrue("小殺棋" in nested)
        assertTrue("牆與真盤邊" in nested)
    }
}
