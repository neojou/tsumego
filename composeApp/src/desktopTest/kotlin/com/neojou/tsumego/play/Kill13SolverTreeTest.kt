package com.neojou.tsumego.play

import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.UnlimitedBudget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** 13K 黑 R19 後樹上不可把 黑下 S19 當白勝（點眼是淨殺）. */
class Kill13SolverTreeTest {
    private val problem = Samples.kill13K.withOpenWallMargin()

    @Test
    fun afterR19TreeMustNotCallS19WhiteWin() = runTest(timeout = 3.minutes) {
        val paths = ArrayList<String>()
        val start = requireNotNull(Position.initial(problem).play(pt("R19"), StoneColor.Black))
        AlphaBetaSolver().solve(
            SolverInput(
                problem = problem,
                position = start,
                consecutivePasses = 0,
                budget = UnlimitedBudget,
                lastBlack = pt("R19"),
                onPath = { paths += it },
            ),
        )
        val dump = paths.joinToString("\n")
        assertTrue(
            paths.none { it.contains("黑下 S19") && it.endsWith("白勝") },
            "S19 is 點眼／黑勝, not 雙活／白勝\n$dump",
        )
        assertTrue(
            paths.any { it.contains("白下 Q19") && it.contains("黑下 S19") && it.endsWith("黑勝") },
            "after Q19, S19 should 淨殺\n$dump",
        )
    }
}
