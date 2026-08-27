package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.boxedProblem
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.openingWhiteLifeProblem
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.Solver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.SolverResult
import com.neojou.tsumego.testSession
import com.neojou.tsumego.ScriptedSolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRulesTest {
    private val atariKill = cornerProblem(
        black = "A2,B1,C2",
        white = "B2",
        goal = Goal.Kill,
        targets = "B2",
    )

    @Test
    fun emptyPointPlacesBlackThenWhiteStubPasses() = runTest {
        val problem = openingWhiteLifeProblem()
        val session = testSession(problem)
        assertTrue(session.tryMove(pt("D1")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(StoneColor.Black, snap.stones[pt("D1")])
        assertEquals(StoneColor.White, snap.stones[pt("A3")])
        assertEquals(PlayStatus.InProgress, snap.status)
        assertEquals(StoneColor.Black, snap.toPlay)
    }

    @Test
    fun captureRemovesOpponentString() = runTest {
        val session = testSession(atariKill)
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        val snap = session.state.value
        assertNull(snap.stones[pt("B2")])
        assertEquals(StoneColor.Black, snap.stones[pt("B3")])
        assertEquals(PlayStatus.Success, snap.status)
    }

    @Test
    fun snapbackIsLegal() = runTest {
        val problem = cornerProblem(
            files = 2,
            ranks = 2,
            black = "A2,B2",
            white = "A1",
            goal = Goal.Kill,
            targets = "A1",
        )
        val session = testSession(problem)
        assertTrue(session.tryMove(pt("B1")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(StoneColor.Black, snap.stones[pt("B1")])
        assertNull(snap.stones[pt("A1")])
        assertEquals(PlayStatus.Success, snap.status)
    }

    @Test
    fun realSuicideIsRejectedAndBoardUnchanged() = runTest {
        val problem = cornerProblem(
            black = "",
            white = "A2,B1,C3",
            goal = Goal.Kill,
            targets = "C3",
        )
        val session = testSession(problem)
        val before = session.state.value.stones
        assertFalse(session.tryMove(pt("A1")))
        assertEquals(before, session.state.value.stones)
        assertEquals(PlayStatus.InProgress, session.state.value.status)
    }

    @Test
    fun occupiedPointAndOutsideRectAreRejected() = runTest {
        val session = testSession(atariKill)
        assertFalse(session.tryMove(pt("B2")))
        assertFalse(session.tryMove(pt("D4")))
        assertEquals(PlayStatus.InProgress, session.state.value.status)
        assertTrue(session.state.value.stones[pt("B2")] == StoneColor.White)
    }

    @Test
    fun positionalSuperkoRejectsRecapture() = runTest {
        val problem = boxedProblem(
            left = "B",
            right = "F",
            bottom = 2,
            top = 6,
            black = "C4,D3,D5,E4",
            white = "E3,E5,F4",
            goal = Goal.Live,
            targets = "C4,D3,D5",
        )
        val session = testSession(
            problem,
            solver = ScriptedSolver(listOf(Action.Move(pt("D4")))),
        )
        assertTrue(session.tryMove(pt("B2")))
        session.waitForIdle()
        assertEquals(StoneColor.White, session.state.value.stones[pt("D4")])
        assertNull(session.state.value.stones[pt("E4")])
        assertFalse(session.tryMove(pt("E4")))
        assertNull(session.state.value.stones[pt("E4")])
        assertEquals(StoneColor.White, session.state.value.stones[pt("D4")])
    }

    @Test
    fun passIsRecordedAndUndoRemovesAPair() = runTest {
        val problem = openingWhiteLifeProblem()
        val session = testSession(problem)
        assertTrue(session.tryMove(pt("D1")))
        session.waitForIdle()
        assertTrue(session.state.value.canUndo)
        assertTrue(session.undo())
        assertEquals(problem.stones, session.state.value.stones)
        assertFalse(session.state.value.canUndo)
        assertEquals(PlayStatus.InProgress, session.state.value.status)
    }

    @Test
    fun redoHidesSearchUntilTheSamePositionIsPlayedAgain() = runTest {
        val path = "白下 B3 -> 白勝"
        var solves = 0
        val solver = object : Solver {
            override suspend fun solve(input: SolverInput): SolverResult {
                solves++
                input.onPath(path)
                input.onPathsComplete()
                return SolverResult.Refute(Action.Move(pt("B3")))
            }
        }
        val problem = openingWhiteLifeProblem()
        val session = testSession(problem, solver = solver)
        assertTrue(session.tryMove(pt("D1")))
        session.waitForIdle()
        assertEquals(PlayStatus.Failure, session.state.value.status)
        assertEquals(1, solves)
        assertEquals(listOf(path), session.state.value.searchPaths)
        assertTrue(session.redo())
        val afterRedo = session.state.value
        assertEquals(PlayStatus.InProgress, afterRedo.status)
        assertEquals(problem.stones, afterRedo.stones)
        assertTrue(afterRedo.searchPaths.isEmpty())
        assertEquals(0, afterRedo.searchPathCount)
        assertTrue(session.tryMove(pt("D1")))
        session.waitForIdle()
        assertEquals(1, solves)
        assertEquals(PlayStatus.Failure, session.state.value.status)
        assertEquals(pt("B3"), session.state.value.lastMove)
        assertEquals(listOf(path), session.state.value.searchPaths)
        assertEquals(1, session.state.value.searchPathCount)
    }

    @Test
    fun redoAfterSuccessLetsBlackStartAgain() = runTest {
        val session = testSession(atariKill)
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
        assertTrue(session.state.value.canRedo)
        assertTrue(session.redo())
        val snap = session.state.value
        assertEquals(PlayStatus.InProgress, snap.status)
        assertEquals(atariKill.stones, snap.stones)
        assertFalse(snap.canUndo)
        assertTrue(session.tryMove(pt("C3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
        assertEquals(StoneColor.Black, session.state.value.stones[pt("C3")])
    }

    @Test
    fun redoWhileInProgressIsRejected() = runTest {
        val session = testSession(atariKill)
        assertFalse(session.redo())
        assertEquals(PlayStatus.InProgress, session.state.value.status)
    }

    @Test
    fun undoCanRepeatBackToTheStart() = runTest {
        val problem = openingWhiteLifeProblem()
        val session = testSession(problem)
        assertTrue(session.tryMove(pt("D1")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertTrue(session.undo())
        assertTrue(session.undo())
        assertEquals(problem.stones, session.state.value.stones)
        assertFalse(session.undo())
    }
}
