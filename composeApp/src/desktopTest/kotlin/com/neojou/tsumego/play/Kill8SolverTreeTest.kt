package com.neojou.tsumego.play

import com.neojou.tsumego.KILL_8K_JSON
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.UnlimitedBudget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** 8K 黑 S17 後樹上不可把 白下 S19 -> 黑下 T18 當白勝（彎三淨殺）. */
class Kill8SolverTreeTest {
    private val problem = run {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_8K_JSON)).problem
        loaded.withOpenWallMargin()
    }

    @Test
    fun afterS17TreeMustNotCallS19T18WhiteWin() = runTest(timeout = 3.minutes) {
        val paths = ArrayList<String>()
        val start = requireNotNull(Position.initial(problem).play(pt("S17"), StoneColor.Black))
        AlphaBetaSolver().solve(
            SolverInput(
                problem = problem,
                position = start,
                consecutivePasses = 0,
                budget = UnlimitedBudget,
                lastBlack = pt("S17"),
                onPath = { paths += it },
            ),
        )
        val dump = paths.joinToString("\n")
        assertTrue(
            paths.none { it.contains("白下 S19") && it.contains("黑下 T18") && it.endsWith("白勝") },
            "T18 is 彎三／黑勝, not 雙活／白勝\n$dump",
        )
        assertTrue(
            paths.any { it.contains("白下 S19") && it.contains("黑下 T18") && it.endsWith("黑勝") },
            "after S19, T18 should 淨殺\n$dump",
        )
    }
}
