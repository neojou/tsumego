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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
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
import com.neojou.tsumego.diagram.toConfirmDraft
import com.neojou.tsumego.io.openDiagramImage
import com.neojou.tsumego.io.openProblemText
import com.neojou.tsumego.io.platformDiagramReader
import com.neojou.tsumego.io.saveProblemText
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.play.CAPTURE_RETRACT_MS
import com.neojou.tsumego.play.PLACE_DROP_MS
import com.neojou.tsumego.play.PlayStatus
import com.neojou.tsumego.play.Session
import com.neojou.tsumego.play.StoneSoundKind
import com.neojou.tsumego.play.playStoneSound
import com.neojou.tsumego.play.stoneFx
import com.neojou.tsumego.play.decisionTreeTitle
import com.neojou.tsumego.play.DecisionTreeLine
import com.neojou.tsumego.play.DecisionTreeView
import com.neojou.tsumego.play.aboutProductSp
import com.neojou.tsumego.play.aboutSummarySp
import com.neojou.tsumego.play.aboutTitle
import com.neojou.tsumego.play.aboutVersionSp
import com.neojou.tsumego.play.emptyPlayHint
import com.neojou.tsumego.play.playHeading
import com.neojou.tsumego.play.redoLabel
import com.neojou.tsumego.play.searchPathCountLabel
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import com.neojou.tsumego.ui.BoardView
import com.neojou.tsumego.ui.ConfirmScreen
import com.neojou.tsumego.ui.playTargetMarks
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

    val topMenus = shellMenuBarItems(
        saveEnabled = currentProblem != null,
        editEnabled = currentProblem != null,
        onBlackFirst = {
            scope.launch { openImport(StoneColor.Black) { d, err -> draft = d; errorMessage = err } }
        },
        onWhiteFirst = {
            scope.launch { openImport(StoneColor.White) { d, err -> draft = d; errorMessage = err } }
        },
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
            val problem = currentProblem ?: return@shellMenuBarItems
            scope.launch {
                val ok = saveProblemText("problem.tsumego.json", ProblemLibrary.encode(problem))
                if (!ok) errorMessage = "無法寫入題目檔"
            }
        },
        onEdit = {
            val problem = currentProblem ?: return@shellMenuBarItems
            draft = problem.toConfirmDraft()
        },
        onAbout = { showAbout = true },
        sampleItems = Samples.all.map { sample ->
            MyTopMenuItem(
                id = "sample-${sample.id}",
                label = sample.name,
                onClick = { startProblem(sample.problem) },
            )
        },
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
                    Text(emptyPlayHint(), style = MaterialTheme.typography.bodyLarge)
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
    var prevStones by remember { mutableStateOf(snap.stones) }
    val dropLift = remember { Animatable(0f) }
    val retractT = remember { Animatable(1f) }
    var dropPt by remember { mutableStateOf<com.neojou.tsumego.board.Point?>(null) }
    var retracting by remember { mutableStateOf(emptyMap<com.neojou.tsumego.board.Point, com.neojou.tsumego.board.StoneColor>()) }
    LaunchedEffect(snap.stones, snap.lastMove, snap.lastMoveIsPass) {
        val undo = snap.lastMove == null && prevStones != snap.stones && !snap.lastMoveIsPass
        val fx = stoneFx(prevStones, snap.stones, snap.lastMove, snap.lastMoveIsPass, undo)
        prevStones = snap.stones
        dropPt = fx.drop
        retracting = fx.retract
        if (fx.drop != null) {
            dropLift.snapTo(1f)
            dropLift.animateTo(0f, tween(PLACE_DROP_MS))
        } else {
            dropLift.snapTo(0f)
        }
        if (StoneSoundKind.Place in fx.sounds) playStoneSound(StoneSoundKind.Place)
        if (fx.retract.isNotEmpty()) {
            retractT.snapTo(0f)
            retractT.animateTo(1f, tween(CAPTURE_RETRACT_MS))
        }
        if (StoneSoundKind.Capture in fx.sounds) playStoneSound(StoneSoundKind.Capture)
        if (fx.retract.isEmpty()) retracting = emptyMap()
    }
    val clickable = snap.status == PlayStatus.InProgress && dropLift.value < 0.08f
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
        Text(snap.problem.goal.playHeading(), style = MaterialTheme.typography.titleMedium)
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
                Text(
                    searchPathCountLabel(displayed = snap.decisionTree.leafCount, total = snap.searchPathCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp),
                )
                if (snap.pickingReply) {
                    Text("    從路徑中思考最強應手...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            PlayStatus.Success, PlayStatus.Failure -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (snap.status == PlayStatus.Success) "成功" else "失敗",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = { session.redo() }) { Text(redoLabel()) }
                OutlinedButton(onClick = { session.undo() }, enabled = snap.canUndo) { Text("悔棋") }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardView(
                rect = snap.problem.rect,
                edges = snap.problem.edges,
                stones = snap.stones,
                targets = playTargetMarks(snap.problem.targets),
                lastMove = snap.lastMove,
                enabled = clickable,
                drop = dropPt,
                dropLift = dropLift.value,
                retract = retracting,
                retractT = retractT.value,
                albedo = rememberBoardAlbedo(),
                onClick = { session.tryMove(it) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SearchPathPane(
                tree = snap.decisionTree,
                modifier = Modifier.width(340.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SearchPathPane(tree: DecisionTreeView, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(tree.lines.size) {
        if (tree.lines.isNotEmpty()) listState.scrollToItem(tree.lines.lastIndex)
    }
    Surface(
        modifier = modifier,
        color = Color(0xFFF3EDE1),
        shape = RoundedCornerShape(2.dp),
    ) {
    Column(Modifier.padding(10.dp)) {
        Text(decisionTreeTitle(), style = MaterialTheme.typography.titleMedium)
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 14.dp),
            ) {
                items(tree.lines) { line: DecisionTreeLine ->
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = (line.indent * 16).dp, bottom = 6.dp),
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
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    aboutTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Text(
                    AppVersion.APP_NAME,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = aboutProductSp().sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                    ),
                )
                Text(
                    AppVersion.SUMMARY,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = aboutSummarySp().sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                )
                Text(
                    AppVersion.DISPLAY,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = aboutVersionSp().sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
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
