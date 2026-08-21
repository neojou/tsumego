package com.neojou.tsumego.play

import com.neojou.tsumego.KILL_8K_JSON
import com.neojou.tsumego.ScriptedSolver
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.classify.firstOwnerMoveToTwoEyes
import com.neojou.tsumego.classify.ownerCanForceLife
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.SolverResult
import com.neojou.tsumego.solve.UnlimitedBudget
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** 8K-kill 黑S18 白T18 黑S19 白S17 is 成功: T17 captures, not 無條件活. */
class Kill8BugLoopTest {
    private val problem = run {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_8K_JSON)).problem
        loaded.withOpenWallMargin()
    }

    private fun dump(pos: Position, label: String): String {
        val white = pos.stones.filterValues { it == StoneColor.White }.keys.sorted()
        val libs = white.flatMap { pos.liberties(pos.stringAt(it)) }.toSet().sorted()
        val outcome = classify(pos, problem.targets, bothPassed = false)
        val liveAt = firstOwnerMoveToTwoEyes(pos, problem.targets)
        val canLive = ownerCanForceLife(pos, problem.targets)
        val targetsOn = problem.targets.filter { it in pos.stones }
        return "$label stonesW=${white.joinToString { it.label }} libs=${libs.joinToString { it.label }} " +
            "outcome=$outcome liveAt=$liveAt canLive=$canLive targetsOn=${targetsOn.joinToString { it.label }} " +
            "t17=${pos.play(pt("T17"), StoneColor.Black) != null}"
    }

    @Test
    fun t17CapturesAfterThePrintedSequence() {
        var pos = Position.initial(problem)
        pos = assertNotNull(pos.play(pt("S18"), StoneColor.Black), dump(pos, "S18 illegal"))
        pos = assertNotNull(pos.play(pt("T18"), StoneColor.White), dump(pos, "T18 illegal"))
        pos = assertNotNull(pos.play(pt("S19"), StoneColor.Black), dump(pos, "S19 illegal"))
        pos = assertNotNull(pos.play(pt("S17"), StoneColor.White), dump(pos, "S17 illegal"))
        val afterS17 = dump(pos, "after S17")
        assertNotEquals(
            Outcome.UnconditionalLive,
            classify(pos, problem.targets, bothPassed = false),
            afterS17,
        )
        val captured = assertNotNull(pos.play(pt("T17"), StoneColor.Black), afterS17)
        val afterT17 = classify(captured, problem.targets, bothPassed = false)
        assertEquals(
            Outcome.UnconditionalDead,
            afterT17,
            "$afterS17 afterT17=$afterT17 remainingW=${captured.stones.filterValues { it == StoneColor.White }.keys}",
        )
        assertTrue(problem.goal.isSuccess(afterT17), afterS17)
    }

    @Test
    fun scriptedT18ThenS17ThenT17IsSuccess() = runTest {
        val session = testSession(
            problem,
            solver = ScriptedSolver(listOf(Action.Move(pt("T18")), Action.Move(pt("S17")))),
        )
        assertTrue(session.tryMove(pt("S18")))
        session.waitForIdle()
        val afterS18 = session.state.value
        assertTrue(session.tryMove(pt("S19")), "after S18 status=${afterS18.status} last=${afterS18.lastMove}")
        session.waitForIdle()
        val afterS19 = session.state.value
        if (afterS19.status == PlayStatus.InProgress) {
            assertTrue(session.tryMove(pt("T17")), "after S19 status=${afterS19.status} last=${afterS19.lastMove}")
            session.waitForIdle()
        }
        val snap = session.state.value
        assertEquals(
            PlayStatus.Success,
            snap.status,
            "after S19 status=${afterS19.status} last=${afterS19.lastMove} " +
                "final=${snap.status} last=${snap.lastMove}",
        )
    }

    @Test
    fun afterS18WhiteResistsAtT18NotS17() = runTest(timeout = 3.minutes) {
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S18")))
        session.waitForIdle()
        val snap = session.state.value
        val dumpS18 = "after S18 status=${snap.status} last=${snap.lastMove} " +
            "white=${snap.stones.filterValues { it == StoneColor.White }.keys.sorted()} " +
            dump(
                Position.initial(problem).play(pt("S18"), StoneColor.Black)!!,
                "pos",
            )
        assertEquals(PlayStatus.InProgress, snap.status, dumpS18)
        assertEquals(pt("T18"), snap.lastMove, dumpS18)
        assertEquals(StoneColor.White, snap.stones[pt("T18")], dumpS18)
        assertEquals(null, snap.stones[pt("S17")], dumpS18)
    }

    @Test
    fun koLineAfterT18IsKillFailureNotSuccess() = runTest(timeout = 3.minutes) {
        val session = testSession(
            problem,
            solver = ScriptedSolver(
                listOf(Action.Move(pt("S18")), Action.Move(pt("T19"))),
            ),
        )
        assertTrue(session.tryMove(pt("T18")))
        session.waitForIdle()
        val afterT18 = session.state.value
        assertTrue(session.tryMove(pt("T17")), "after T18 status=${afterT18.status} last=${afterT18.lastMove}")
        session.waitForIdle()
        val afterT17 = session.state.value
        assertTrue(session.tryMove(pt("S19")), "after T17 status=${afterT17.status} last=${afterT17.lastMove}")
        session.waitForIdle()
        var pos = Position.initial(problem)
        pos = assertNotNull(pos.play(pt("T18"), StoneColor.Black))
        pos = assertNotNull(pos.play(pt("S18"), StoneColor.White))
        pos = assertNotNull(pos.play(pt("T17"), StoneColor.Black))
        pos = assertNotNull(pos.play(pt("T19"), StoneColor.White))
        pos = assertNotNull(pos.play(pt("S19"), StoneColor.Black))
        val outcome = classify(pos, problem.targets, bothPassed = false)
        val snap = session.state.value
        assertEquals(
            PlayStatus.Failure,
            snap.status,
            "status=${snap.status} last=${snap.lastMove} outcome=$outcome " +
                dump(pos, "ko"),
        )
        assertTrue(
            !problem.goal.isSuccess(outcome),
            "kill must not pass on 打劫; outcome=$outcome ${dump(pos, "ko")}",
        )
    }

    @Test
    fun sessionAfterS18ThenS19MustNotFail() = runTest(timeout = 3.minutes) {
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S18")))
        session.waitForIdle()
        val afterS18 = session.state.value
        val dumpS18 = "after S18 status=${afterS18.status} last=${afterS18.lastMove} " +
            "white=${afterS18.stones.filterValues { it == StoneColor.White }.keys.sorted()}"
        assertNotEquals(PlayStatus.Failure, afterS18.status, dumpS18)
        assertTrue(session.tryMove(pt("S19")), dumpS18)
        session.waitForIdle()
        val snap = session.state.value
        val dumpS19 = "after S19 status=${snap.status} last=${snap.lastMove} " +
            "white=${snap.stones.filterValues { it == StoneColor.White }.keys.sorted()} " +
            "paths=${snap.searchPaths.takeLast(8)}"
        assertEquals(PlayStatus.Success, snap.status, dumpS19)
    }

    @Test
    fun solverAfterS19MustNotRefute() = runTest(timeout = 3.minutes) {
        var pos = Position.initial(problem)
        pos = assertNotNull(pos.play(pt("S18"), StoneColor.Black))
        pos = assertNotNull(pos.play(pt("T18"), StoneColor.White))
        pos = assertNotNull(pos.play(pt("S19"), StoneColor.Black))
        val before = dump(pos, "toPlay white after S19")
        val result = AlphaBetaSolver().solve(
            SolverInput(
                problem = problem,
                position = pos,
                consecutivePasses = 0,
                budget = UnlimitedBudget,
            ),
        )
        assertTrue(
            result !is SolverResult.Refute,
            "$before result=$result",
        )
    }
}
