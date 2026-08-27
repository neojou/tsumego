package com.neojou.tsumego

import com.neojou.tools.ui.menu.MyTopMenuItem
import com.neojou.tsumego.library.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellMenuTest {
    @Test
    fun topMenusAreImportFileEditAbout() {
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
        assertEquals(listOf("Import", "File", "Edit", "About"), items.map { it.label })
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
            sampleItems = Samples.all.map { sample ->
                MyTopMenuItem(id = "sample-${sample.id}", label = sample.name, onClick = {})
            },
        )
        assertEquals(listOf("Open", "Save", "Samples"), items.map { it.label })
        val nested = items.flatMap { it.children }.map { it.label }
        assertEquals(listOf("15K 殺棋", "13K 殺棋", "8K 殺棋", "7K 老鼠偷油"), nested)
        assertTrue("角上做活" !in nested)
        assertTrue("小殺棋" !in nested)
        assertTrue("牆與真盤邊" !in nested)
    }
}
