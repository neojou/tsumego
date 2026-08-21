package com.neojou.tsumego.play

import com.neojou.tsumego.SMALL_TRICK_JSON
import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmallTrickPlayTest {
    private val problem = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(SMALL_TRICK_JSON)).problem

    @Test
    fun savedFileIsABlackToPlayKillInTheUpperRight() {
        assertEquals(Goal.Kill, problem.goal)
        assertEquals(pt("O14").file, problem.rect.left)
        assertEquals(pt("T19").file, problem.rect.right)
        assertEquals(14, problem.rect.bottom)
        assertEquals(19, problem.rect.top)
        assertEquals(StoneColor.White, problem.stones[pt("T18")])
        assertEquals(StoneColor.Black, problem.stones[pt("Q19")])
        assertTrue(pt("T18") in problem.targets)
        assertEquals(null, problem.validationError())
    }

    @Test
    fun s19IsALegalBlackMoveOnTheImportedKill() = runTest {
        val session = testSession(problem)
        assertTrue(session.tryMove(pt("S19")))
        session.waitForIdle()
        assertEquals(StoneColor.Black, session.state.value.stones[pt("S19")])
        assertTrue(session.state.value.status == PlayStatus.InProgress || session.state.value.status == PlayStatus.Success)
    }

    @Test
    fun capturingR17R18LeavesS16AndT18SoKillIsNotYetSuccess() = runTest {
        val session = testSession(problem)
        assertTrue(session.tryMove(pt("S19")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("R19")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("S18")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("S17")))
        session.waitForIdle()
        val snap = session.state.value
        assertNull(snap.stones[pt("R17")])
        assertNull(snap.stones[pt("R18")])
        assertEquals(StoneColor.White, snap.stones[pt("S16")])
        assertEquals(StoneColor.White, snap.stones[pt("T18")])
        assertNotEquals(PlayStatus.Success, snap.status)
    }

    @Test
    fun capturingTheOnlyMarkedWhiteStringIsImmediateSuccess() = runTest {
        val narrowed = problem.copy(targets = setOf(pt("R17"), pt("R18")))
        val session = testSession(narrowed)
        assertTrue(session.tryMove(pt("S19")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("R19")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("S18")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("S17")))
        session.waitForIdle()
        val snap = session.state.value
        assertNull(snap.stones[pt("R17")])
        assertNull(snap.stones[pt("R18")])
        assertEquals(PlayStatus.Success, snap.status)
    }
}
