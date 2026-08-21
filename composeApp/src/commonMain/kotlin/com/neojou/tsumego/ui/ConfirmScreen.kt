package com.neojou.tsumego.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.diagram.ConfirmDraft
import com.neojou.tsumego.rememberBoardAlbedo

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
            Text("題型：殺棋", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
            FilterChip(
                selected = targetMode,
                onClick = { targetMode = !targetMode },
                label = { Text(if (targetMode) "標目標中（${draft.targets.size}）" else "標目標") },
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
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                targetListLabel(draft.targets),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(min = 140.dp).width(200.dp).fillMaxHeight(),
            )
            BoardView(
                rect = draft.rect,
                edges = draft.edges,
                stones = draft.stones,
                targets = confirmTargetMarks(draft.targets),
                overlayImage = image,
                imageGrid = draft.imageGrid,
                albedo = rememberBoardAlbedo(),
                onClick = { point ->
                    onChange(draft.applyClick(point, targetMode))
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
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
