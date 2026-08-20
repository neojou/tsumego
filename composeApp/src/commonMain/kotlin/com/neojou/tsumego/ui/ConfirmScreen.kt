package com.neojou.tsumego.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.diagram.ConfirmDraft

@Composable
fun ConfirmScreen(
    draft: ConfirmDraft,
    onChange: (ConfirmDraft) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var targetMode by remember { mutableStateOf(false) }
    val image = remember(draft.imageBytes) { draft.imageBytes?.let { decodeBoardImage(it) } }
    val error = draft.validationError()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GoalPicker(draft.goal) { onChange(draft.copy(goal = it)) }
            FilterChip(
                selected = targetMode,
                onClick = { targetMode = !targetMode },
                label = { Text(if (targetMode) "標目標中" else "標目標") },
            )
            EdgeChip("左", draft.edges.left) {
                onChange(draft.copy(edges = draft.edges.copy(left = it)))
            }
            EdgeChip("右", draft.edges.right) {
                onChange(draft.copy(edges = draft.edges.copy(right = it)))
            }
            EdgeChip("下", draft.edges.bottom) {
                onChange(draft.copy(edges = draft.edges.copy(bottom = it)))
            }
            EdgeChip("上", draft.edges.top) {
                onChange(draft.copy(edges = draft.edges.copy(top = it)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoundPicker("左", Point.FILE_CHARS[draft.rect.left].toString(), Point.FILE_CHARS.map { it.toString() }) { label ->
                val file = Point.fileIndex(label[0]) ?: return@BoundPicker
                if (file <= draft.rect.right) onChange(draft.withRect(draft.rect.copy(left = file)))
            }
            BoundPicker("右", Point.FILE_CHARS[draft.rect.right].toString(), Point.FILE_CHARS.map { it.toString() }) { label ->
                val file = Point.fileIndex(label[0]) ?: return@BoundPicker
                if (file >= draft.rect.left) onChange(draft.withRect(draft.rect.copy(right = file)))
            }
            BoundPicker("下", draft.rect.bottom.toString(), (1..19).map { it.toString() }) { label ->
                val rank = label.toInt()
                if (rank <= draft.rect.top) onChange(draft.withRect(draft.rect.copy(bottom = rank)))
            }
            BoundPicker("上", draft.rect.top.toString(), (1..19).map { it.toString() }) { label ->
                val rank = label.toInt()
                if (rank >= draft.rect.bottom) onChange(draft.withRect(draft.rect.copy(top = rank)))
            }
        }
        BoardView(
            rect = draft.rect,
            edges = draft.edges,
            stones = draft.stones,
            targets = draft.targets,
            overlayImage = image,
            onClick = { point ->
                onChange(if (targetMode) draft.toggleTarget(point) else draft.cycleStone(point))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConfirm, enabled = error == null) { Text("確認") }
            OutlinedButton(onClick = onCancel) { Text("取消") }
        }
    }
}

@Composable
private fun GoalPicker(goal: Goal, onPick: (Goal) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text("題型：${goal.label()}") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Goal.entries.forEach { g ->
            DropdownMenuItem(
                text = { Text(g.label()) },
                onClick = {
                    expanded = false
                    onPick(g)
                },
            )
        }
    }
}

@Composable
private fun EdgeChip(label: String, kind: EdgeKind, onToggle: (EdgeKind) -> Unit) {
    FilterChip(
        selected = kind == EdgeKind.Real,
        onClick = { onToggle(if (kind == EdgeKind.Real) EdgeKind.Wall else EdgeKind.Real) },
        label = { Text("$label ${if (kind == EdgeKind.Real) "真盤邊" else "牆"}") },
    )
}

@Composable
private fun BoundPicker(label: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text("$label $current") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                    expanded = false
                    onPick(option)
                },
            )
        }
    }
}

fun Goal.label(): String = when (this) {
    Goal.Live -> "做活"
    Goal.Kill -> "殺棋"
    Goal.Seki -> "雙活題"
    Goal.KoLive -> "劫活"
    Goal.KoKill -> "劫殺"
}
