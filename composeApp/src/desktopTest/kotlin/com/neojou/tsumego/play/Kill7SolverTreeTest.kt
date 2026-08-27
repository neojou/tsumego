package com.neojou.tsumego.play

import com.neojou.tsumego.KILL_7K_ONE_MORE_JSON
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.UnlimitedBudget
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Full 7K search after S17 is longer than Mocha's 2s wasm cap
 * (`runTest(3.minutes)` does not extend it). Same split as SmallTrickSolverTest.
 */
class Kill7SolverTreeTest {
    private val problem = run {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_7K_ONE_MORE_JSON)).problem
        loaded.withOpenWallMargin()
    }

    @Test
    fun afterS17TreeMustNotCallT19WhiteWin() = runTest(timeout = 3.minutes) {
        val paths = ArrayList<String>()
        val pvs = ArrayList<String>()
        val start = Position.initial(problem).play(pt("S17"), StoneColor.Black)!!
        val solver = AlphaBetaSolver()
        solver.solve(
            SolverInput(
                problem = problem,
                position = start,
                consecutivePasses = 0,
                budget = UnlimitedBudget,
                onPath = { paths += it },
                onPv = { _, _, continuation, _ -> pvs += continuation },
            ),
        )
        val dump = "paths=\n${paths.joinToString("\n")}\npvs=\n${pvs.joinToString("\n")}"
        assertTrue(
            paths.none { it == "白下 T17 -> 黑下 T18 -> 白下 T19 -> 白勝" },
            "T19 is not a 白勝 leaf; R19 still captures\n$dump",
        )
        assertTrue(
            paths.any { it.startsWith("白下 T17 -> 黑下 T18 -> 白下 T19 -> 黑下 R19") },
            "after T19 black must still be allowed R19\n$dump",
        )
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S17")))
        session.waitForIdle()
        val treeLines = session.state.value.decisionTree.lines.map { it.text }
        assertTrue(
            treeLines.none { it == "白下 T19 -> 白勝" },
            "決策樹 must not treat T19 as 白勝續線\n${treeLines.joinToString("\n")}\n$dump",
        )
    }
}
