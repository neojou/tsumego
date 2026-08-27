package com.neojou.tsumego.play

import com.neojou.tsumego.KILL_7K_ONE_MORE_JSON
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.SolverResult
import com.neojou.tsumego.solve.UnlimitedBudget
import com.neojou.tsumego.solve.actions
import com.neojou.tsumego.solve.timeBudget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Phase timings: first 搜尋路徑 vs 證明完 vs 應手. */
class Kill7ReplyLagTest {
    private val problem = run {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_7K_ONE_MORE_JSON)).problem
        loaded.withOpenWallMargin()
    }

    @Test
    fun afterS17PathsAppearBeforeReply() = runTest(timeout = 3.minutes) {
        val start = Position.initial(problem).play(pt("S17"), StoneColor.Black)!!
        val whiteMoves = actions(start, StoneColor.White, problem)
        val clock = TimeSource.Monotonic
        val t0 = clock.markNow()
        var firstPath: Duration? = null
        var complete: Duration? = null
        var pathCount = 0
        val result = AlphaBetaSolver().solve(
            SolverInput(
                problem = problem,
                position = start,
                consecutivePasses = 0,
                budget = UnlimitedBudget,
                onPath = {
                    pathCount++
                    if (firstPath == null) firstPath = t0.elapsedNow()
                },
                onPathsComplete = { complete = t0.elapsedNow() },
            ),
        )
        val done = t0.elapsedNow()
        val dump =
            "whiteMoves=${whiteMoves.filterIsInstance<Action.Move>().map { it.point.label }} " +
                "nWhite=${whiteMoves.size} firstPath=$firstPath complete=$complete done=$done " +
                "paths=$pathCount result=$result lagAfterFirst=${firstPath?.let { done - it }} " +
                "pickAfterComplete=${complete?.let { done - it }}"
        assertTrue(pathCount > 0, "expected 搜尋路徑 before 應手\n$dump")
        assertTrue(complete != null, "onPathsComplete never fired\n$dump")
        assertTrue(firstPath != null && firstPath <= complete, "paths should stream before proof ends\n$dump")
        assertTrue(result is SolverResult.Resist || result is SolverResult.Refute, dump)
        assertTrue(complete <= done, dump)
    }

    @Test
    fun mouseOilAfterT16MustLandReplyAfterPicking() = runTest(timeout = 45.seconds) {
        val problem = Samples.kill7K.withOpenWallMargin()
        val start = Position.initial(problem).play(pt("T16"), StoneColor.Black)!!
        val whiteMoves = actions(start, StoneColor.White, problem)
        val clock = TimeSource.Monotonic
        val t0 = clock.markNow()
        var firstPath: Duration? = null
        var complete: Duration? = null
        var pathCount = 0
        val result = AlphaBetaSolver().solve(
            SolverInput(
                problem = problem,
                position = start,
                consecutivePasses = 0,
                budget = timeBudget(25_000),
                onPath = {
                    pathCount++
                    if (firstPath == null) firstPath = t0.elapsedNow()
                },
                onPathsComplete = { complete = t0.elapsedNow() },
            ),
        )
        val done = t0.elapsedNow()
        val dump =
            "whiteMoves=${whiteMoves.filterIsInstance<Action.Move>().map { it.point.label }} " +
                "nWhite=${whiteMoves.size} firstPath=$firstPath complete=$complete done=$done " +
                "paths=$pathCount result=$result pickAfterComplete=${complete?.let { done - it }}"
        assertTrue(complete != null, "證明未在 25s 內結束（還不是選應手階段）\n$dump")
        assertTrue(
            result is SolverResult.Resist || result is SolverResult.Refute,
            "從路徑中思考最強應手 afterwards still no 應手\n$dump",
        )
    }
}
