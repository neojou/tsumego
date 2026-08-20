package com.neojou.tsumego

import com.neojou.tools.ui.menu.MyTopMenuItem

fun fileMenuItems(
    saveEnabled: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
): List<MyTopMenuItem> = listOf(
    MyTopMenuItem(id = "open", label = "Open", onClick = onOpen),
    MyTopMenuItem(id = "save", label = "Save", enabled = saveEnabled, onClick = onSave),
)
