package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.pt
import com.neojou.tsumego.ImmediateTimeoutSolver
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.Budget
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionSolverTest {
    private val solver = AlphaBetaSolver(maxDepth = 12)

    @Test
    fun whiteRepliesWithLongestResistanceAndStableCoordinates() = runTest {
        val problem = cornerProblem(
            files = 3,
            ranks = 3,
            black = "A2,B1,C2",
            white = "B2",
            goal = Goal.Kill,
            targets = "B2",
        )
        val session = testSession(problem, solver = solver)
        assertTrue(session.tryMove(pt("C3")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(PlayStatus.InProgress, snap.status)
        assertEquals(pt("B3"), snap.lastMove)
        assertEquals(StoneColor.White, snap.stones[pt("B3")])
        assertEquals(StoneColor.White, snap.stones[pt("B2")])
    }

    @Test
    fun failureShowsRefutationThenFailure() = runTest {
        val problem = cornerProblem(
            files = 3,
            ranks = 3,
            black = "B2",
            white = "A2,B1,C2",
            goal = Goal.Live,
            targets = "B2",
        )
        val session = testSession(problem, solver = solver)
        assertTrue(session.tryMove(pt("C3")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(PlayStatus.Failure, snap.status)
        assertTrue(snap.lastMove != null || snap.lastMoveIsPass)
        if (snap.lastMove == pt("B3")) {
            assertNull(snap.stones[pt("B2")])
        }
    }

    @Test
    fun koLiveProblemSucceedsOnUnconditionalLive() = runTest {
        val session = testSession(Samples.liveCorner.copy(goal = Goal.KoLive), solver = solver)
        assertTrue(session.pass())
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun timeoutLeavesPositionUnsettledWithoutAWhiteMove() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
            solver = ImmediateTimeoutSolver,
        )
        assertTrue(session.tryMove(pt("A1")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(PlayStatus.Timeout, snap.status)
        assertEquals(pt("A1"), snap.lastMove)
        assertNull(snap.stones[pt("A3")])
        assertNull(snap.stones[pt("C1")])
        assertNull(snap.stones[pt("C3")])
        assertTrue(snap.canAddTime)
        assertTrue(snap.canUndo)
    }

    @Test
    fun addTimeRerunsSearchAfterTimeout() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
            solver = ImmediateTimeoutSolver,
        )
        assertTrue(session.tryMove(pt("A1")))
        session.waitForIdle()
        assertEquals(PlayStatus.Timeout, session.state.value.status)
        assertTrue(session.addTime())
        session.waitForIdle()
        assertEquals(PlayStatus.Timeout, session.state.value.status)
    }

    @Test
    fun undoWorksAfterTimeout() = runTest {
        val problem = cornerProblem(
            black = "A2,B1,C2",
            white = "B2",
            goal = Goal.Kill,
            targets = "B2",
        )
        val session = testSession(problem, solver = ImmediateTimeoutSolver)
        assertTrue(session.tryMove(pt("A1")))
        session.waitForIdle()
        assertTrue(session.undo())
        assertEquals(problem.stones, session.state.value.stones)
        assertEquals(PlayStatus.InProgress, session.state.value.status)
        assertFalse(session.state.value.canAddTime)
    }

    @Test
    fun expiredBudgetOnRealSolverIsTimeout() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
            solver = solver,
            budget = Budget { true },
        )
        assertTrue(session.tryMove(pt("A1")))
        session.waitForIdle()
        assertEquals(PlayStatus.Timeout, session.state.value.status)
    }

    @Test
    fun koKillProblemSucceedsOnUnconditionalDead() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.KoKill,
                targets = "B2",
            ),
            solver = solver,
        )
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
        assertNull(session.state.value.stones[pt("B2")])
    }

    @Test
    fun capturingTheLastLibertyStillSucceedsWithRealSolver() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
            solver = solver,
        )
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
        assertNull(session.state.value.stones[pt("B2")])
    }
}
