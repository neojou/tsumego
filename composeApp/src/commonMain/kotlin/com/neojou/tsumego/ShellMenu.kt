package com.neojou.tsumego

import com.neojou.tools.ui.menu.MyTopMenuItem

fun fileMenuItems(
    saveEnabled: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    sampleItems: List<MyTopMenuItem> = emptyList(),
): List<MyTopMenuItem> = listOf(
    MyTopMenuItem(id = "open", label = "Open", onClick = onOpen),
    MyTopMenuItem(id = "save", label = "Save", enabled = saveEnabled, onClick = onSave),
    MyTopMenuItem(id = "samples", label = "Samples", children = sampleItems),
)

fun shellMenuBarItems(
    saveEnabled: Boolean,
    editEnabled: Boolean,
    onBlackFirst: () -> Unit,
    onWhiteFirst: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onAbout: () -> Unit,
    sampleItems: List<MyTopMenuItem> = emptyList(),
): List<MyTopMenuItem> = listOf(
    MyTopMenuItem(
        id = "input",
        label = "Input",
        children = listOf(
            MyTopMenuItem(id = "black-first", label = "Black First", onClick = onBlackFirst),
            MyTopMenuItem(id = "white-first", label = "White First", onClick = onWhiteFirst),
        ),
    ),
    MyTopMenuItem(
        id = "file",
        label = "File",
        children = fileMenuItems(
            saveEnabled = saveEnabled,
            onOpen = onOpen,
            onSave = onSave,
            sampleItems = sampleItems,
        ),
    ),
    MyTopMenuItem(id = "edit", label = "Edit", enabled = editEnabled, onClick = onEdit),
    MyTopMenuItem(id = "about", label = "About", onClick = onAbout),
)
