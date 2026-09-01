package com.neojou.tsumego.play

import com.neojou.tsumego.SMALL_TRICK_JSON
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import com.neojou.tsumego.smallTrickPlayable
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SmallTrickSolverTest {
    private val playable = smallTrickPlayable()

    @Test
    fun afterS19WhiteResistsAtR19() = runTest(timeout = 3.minutes) {
        val session = testSession(playable, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S19")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(
            PlayStatus.InProgress,
            snap.status,
            "expected 應手, status=${snap.status} last=${snap.lastMove}",
        )
        assertEquals(pt("R19"), snap.lastMove, "白應手 should be R19, got ${snap.lastMove}")
    }

    @Test
    fun afterS19R19WrongS15WhiteLivesAtT16() = runTest(timeout = 3.minutes) {
        val session = testSession(playable, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S19")))
        session.waitForIdle()
        assertEquals(pt("R19"), session.state.value.lastMove)
        assertEquals(PlayStatus.InProgress, session.state.value.status)
        assertTrue(session.tryMove(pt("S15")))
        session.waitForIdle()
        val snap = session.state.value
        val dump = "status=${snap.status} last=${snap.lastMove} tree=${snap.decisionTree.lines.map { it.text }}"
        assertEquals(PlayStatus.InProgress, snap.status, dump)
        assertEquals(pt("T16"), snap.lastMove, "白應 T16（做活還差 S17，黑能回來淨殺，不是立刻失敗）\n$dump")
        assertEquals(StoneColor.White, snap.stones[pt("T16")], dump)
    }

    @Test
    fun tenukiS13IsRefutedByTwoEyesAtS19() = runTest(timeout = 3.minutes) {
        val session = testSession(playable, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S13")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(PlayStatus.Failure, snap.status)
        assertEquals(pt("S19"), snap.lastMove, "白應兩眼做活於 S19, got ${snap.lastMove}")
        assertEquals(StoneColor.White, snap.stones[pt("S19")])
    }

    @Test
    fun firstMoveRecordsSearchPaths() = runTest(timeout = 3.minutes) {
        val session = testSession(playable, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S19")))
        session.waitForIdle()
        val snap = session.state.value
        assertTrue(snap.status != PlayStatus.Timeout)
        assertTrue(snap.searchPaths.isNotEmpty(), "expected 搜尋路徑, status=${snap.status}")
    }

    @Test
    fun distantBlackMoveIsRefutedInTheFight() = runTest {
        val problem = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(SMALL_TRICK_JSON)).problem.withOpenWallMargin()
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("R12")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(PlayStatus.Failure, snap.status)
        val white = snap.lastMove
        assertTrue(white != null, "expected 反駁手")
        assertTrue(
            white.rank >= 16 || white == pt("S19") || white == pt("T19") || white == pt("S17"),
            "反駁手 should be in the fight, got $white",
        )
    }
}
