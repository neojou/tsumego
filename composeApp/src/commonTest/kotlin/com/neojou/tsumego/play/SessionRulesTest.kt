package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.boxedProblem
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
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
        val session = testSession(atariKill)
        assertTrue(session.tryMove(pt("A1")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(StoneColor.Black, snap.stones[pt("A1")])
        assertEquals(StoneColor.White, snap.stones[pt("B2")])
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
        val session = testSession(atariKill)
        assertTrue(session.tryMove(pt("A1")))
        session.waitForIdle()
        assertTrue(session.state.value.canUndo)
        assertTrue(session.undo())
        assertEquals(atariKill.stones, session.state.value.stones)
        assertFalse(session.state.value.canUndo)
        assertEquals(PlayStatus.InProgress, session.state.value.status)
    }

    @Test
    fun undoCanRepeatBackToTheStart() = runTest {
        val session = testSession(atariKill)
        assertTrue(session.tryMove(pt("A1")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("C1")))
        session.waitForIdle()
        assertTrue(session.undo())
        assertTrue(session.undo())
        assertEquals(atariKill.stones, session.state.value.stones)
        assertFalse(session.undo())
    }
}
