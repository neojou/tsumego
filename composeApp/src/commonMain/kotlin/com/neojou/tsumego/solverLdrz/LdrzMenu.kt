package com.neojou.tsumego.solverLdrz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neojou.tools.ui.menu.MyTopMenuItem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.rememberBoardAlbedo
import com.neojou.tsumego.ui.BoardView

fun ldrzMenuBarItem(
    enabled: Boolean,
    onOpen: () -> Unit,
    onCalculate: () -> Unit,
    onShow: () -> Unit,
): MyTopMenuItem = MyTopMenuItem(
    id = "study-ld-rz",
    label = "study-LD-RZ",
    enabled = enabled,
    children = listOf(
        MyTopMenuItem(id = "ldrz-open", label = "Open", enabled = enabled, onClick = onOpen),
        MyTopMenuItem(id = "ldrz-calculate", label = "Calculate", enabled = enabled, onClick = onCalculate),
        MyTopMenuItem(id = "ldrz-show", label = "Show", enabled = enabled, onClick = onShow),
    ),
)

@Composable
fun LdrzBoardScreen(session: LdrzSession, modifier: Modifier = Modifier) {
    val problem = session.problem ?: return
    val (rect, edges) = problem.geometry()
    val firstMove = session.result?.firstMove
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("study-LD-RZ  ${problem.stem}", style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                append(if (problem.turnColor == StoneColor.Black) "輪黑" else "輪白")
                append("  黑")
                append(problem.blackGoal.name)
                append("  白")
                append(problem.whiteGoal.name)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (session.calculating) {
            Text("計算中…", style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardView(
                rect = rect,
                edges = edges,
                stones = problem.stones.filterKeys { rect.contains(it) },
                targets = problem.crucialStones(),
                lastMove = firstMove,
                regionMarks = problem.regionMarks(),
                enabled = false,
                albedo = rememberBoardAlbedo(),
                onClick = {},
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Surface(
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                color = Color(0xFFF3EDE1),
                shape = RoundedCornerShape(2.dp),
            ) {
                val scroll = rememberScrollState()
                Column(Modifier.padding(10.dp).verticalScroll(scroll)) {
                    Text("結果", style = MaterialTheme.typography.titleMedium)
                    Text(
                        session.showText
                            ?: "Open 後顯示詰棋（關鍵子紅圈、region 方標）。Calculate 寫 result/。Show 讀回狀態／第一手。",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
