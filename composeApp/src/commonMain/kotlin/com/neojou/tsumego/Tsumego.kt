package com.neojou.tsumego

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import com.neojou.tools.ui.menu.MyTopMenuBar
import com.neojou.tools.ui.menu.MyTopMenuItem
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.diagram.ConfirmDraft
import com.neojou.tsumego.diagram.readDiagram
import com.neojou.tsumego.io.openDiagramImage
import com.neojou.tsumego.io.openProblemText
import com.neojou.tsumego.io.platformDiagramReader
import com.neojou.tsumego.io.saveProblemText
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.play.PlayStatus
import com.neojou.tsumego.play.Session
import com.neojou.tsumego.play.numberedSearchPaths
import com.neojou.tsumego.play.playHeading
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import com.neojou.tsumego.ui.BoardView
import com.neojou.tsumego.ui.ConfirmScreen
import com.neojou.tsumego.ui.label
import kotlinx.coroutines.launch

private const val TAG = "Tsumego"

@Composable
fun Tsumego() {
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Session?>(null) }
    var draft by remember { mutableStateOf<ConfirmDraft?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentProblem by remember { mutableStateOf<Problem?>(null) }

    fun startProblem(problem: Problem) {
        val playable = problem.withOpenWallMargin()
        val err = playable.validationError()
        if (err != null) {
            errorMessage = err
            return
        }
        currentProblem = playable
        draft = null
        session = Session(problem = playable, scope = scope)
    }

    val topMenus = listOf(
        MyTopMenuItem(
            id = "input",
            label = "Input",
            children = listOf(
                MyTopMenuItem(
                    id = "black-first",
                    label = "Black First",
                    onClick = {
                        scope.launch { openImport(StoneColor.Black) { d, err -> draft = d; errorMessage = err } }
                    },
                ),
                MyTopMenuItem(
                    id = "white-first",
                    label = "White First",
                    onClick = {
                        scope.launch { openImport(StoneColor.White) { d, err -> draft = d; errorMessage = err } }
                    },
                ),
            ),
        ),
        MyTopMenuItem(
            id = "file",
            label = "File",
            children = fileMenuItems(
                saveEnabled = currentProblem != null,
                onOpen = {
                    scope.launch {
                        val text = openProblemText() ?: return@launch
                        when (val loaded = ProblemLibrary.decode(text)) {
                            is ProblemLoad.Ok -> startProblem(loaded.problem)
                            is ProblemLoad.Err -> errorMessage = loaded.message
                        }
                    }
                },
                onSave = {
                    val problem = currentProblem ?: return@fileMenuItems
                    scope.launch {
                        val ok = saveProblemText("problem.tsumego.json", ProblemLibrary.encode(problem))
                        if (!ok) errorMessage = "無法寫入題目檔"
                    }
                },
            ),
        ),
        MyTopMenuItem(
            id = "about",
            label = "About",
            onClick = { showAbout = true },
        ),
    )

    LaunchedEffect(Unit) {
        MyLog.add(TAG, "Enter", LogLevel.DEBUG)
    }

    Scaffold(
        topBar = { MyTopMenuBar(items = topMenus) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val currentDraft = draft
            val currentSession = session
            when {
                currentDraft != null -> ConfirmScreen(
                    draft = currentDraft,
                    onChange = { draft = it },
                    onConfirm = {
                        val problem = currentDraft.toProblem()
                        val err = problem.validationError()
                        if (err != null) errorMessage = err else startProblem(problem)
                    },
                    onCancel = { draft = null },
                )
                currentSession != null -> PlayScreen(currentSession)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("請用 Input 匯入棋譜圖，或 File → Open 打開題目檔")
                }
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    val message = errorMessage
    if (message != null) {
        MessageDialog(message = message, onDismiss = { errorMessage = null })
    }
}

private suspend fun openImport(diagramFirst: StoneColor, done: (ConfirmDraft?, String?) -> Unit) {
    val picked = openDiagramImage() ?: return
    val draft = readDiagram(platformDiagramReader(), picked.bytes, diagramFirst)
        .copy(imageBytes = picked.bytes)
    done(draft, null)
}

@Composable
private fun PlayScreen(session: Session) {
    val snap by session.state.collectAsState()
    val clickable = snap.status == PlayStatus.InProgress
    var thinkSec by remember { mutableStateOf(0L) }
    LaunchedEffect(snap.status) {
        thinkSec = 0
        if (snap.status != PlayStatus.WaitingForReply) return@LaunchedEffect
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            thinkSec = start.elapsedNow().inWholeSeconds
            delay(200)
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(snap.problem.goal.playHeading(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        when (snap.status) {
            PlayStatus.InProgress -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("輪黑, 請落子", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { session.pass() }) { Text("停") }
                OutlinedButton(onClick = { session.undo() }, enabled = snap.canUndo) { Text("悔棋") }
            }
            PlayStatus.WaitingForReply, PlayStatus.Timeout -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("輪白, 思考時間：$thinkSec  秒", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { session.pass() }) { Text("停") }
                }
                Text("目前動作 :", style = MaterialTheme.typography.bodyMedium)
                Text("    搜尋路徑數目： ${snap.searchPaths.size}", style = MaterialTheme.typography.bodyMedium)
                if (snap.pickingReply) {
                    Text("    從路徑中思考最強應手...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            PlayStatus.Success -> Text("成功", style = MaterialTheme.typography.bodyLarge)
            PlayStatus.Failure -> Text("失敗", style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardView(
                rect = snap.problem.rect,
                edges = snap.problem.edges,
                stones = snap.stones,
                lastMove = snap.lastMove,
                enabled = clickable,
                onClick = { session.tryMove(it) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SearchPathPane(paths = snap.searchPaths, modifier = Modifier.width(340.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun SearchPathPane(paths: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val numbered = numberedSearchPaths(paths)
    LaunchedEffect(paths.size) {
        if (numbered.isNotEmpty()) listState.scrollToItem(numbered.lastIndex)
    }
    Column(modifier) {
        Text("搜尋路徑", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 14.dp),
            ) {
                items(numbered) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(AppVersion.APP_NAME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    AppVersion.APP_NAME_EN,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Text(
                    "版本 ${AppVersion.DISPLAY}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    AppVersion.SUMMARY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("關閉")
                }
            }
        }
    }
}

@Composable
private fun MessageDialog(message: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("關閉")
                }
            }
        }
    }
}
