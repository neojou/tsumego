package com.neojou.tsumego.play

import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.boxedProblem
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.pt
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionOutcomeTest {
    @Test
    fun killSucceedsWhenAllTargetStonesAreCaptured() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
        )
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertNull(session.state.value.stones[pt("B2")])
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun liveSucceedsOnBensonLifeAfterPass() = runTest {
        val session = testSession(Samples.liveCorner)
        assertTrue(session.pass())
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun liveFailsWhenTargetsAreDeadAfterBothPass() = runTest {
        val session = testSession(
            cornerProblem(
                files = 3,
                ranks = 3,
                black = "B2",
                white = "A2,B1,C2",
                goal = Goal.Live,
                targets = "B2",
            ),
        )
        assertTrue(session.pass())
        session.waitForIdle()
        assertEquals(PlayStatus.Failure, session.state.value.status)
    }

    @Test
    fun sekiProblemSucceedsOnlyOnSeki() = runTest {
        val seki = boxedProblem(
            left = "A",
            right = "E",
            bottom = 1,
            top = 3,
            black = "A1,A2,A3,B1,B3",
            white = "D1,D3,E1,E2,E3",
            goal = Goal.Seki,
            targets = "A1,A2,A3,B1,B3,D1,D3,E1,E2,E3",
            leftEdge = EdgeKind.Real,
            bottomEdge = EdgeKind.Real,
        )
        val session = testSession(seki)
        assertTrue(session.pass())
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun liveProblemDoesNotSucceedOnSekiShape() = runTest {
        val live = boxedProblem(
            left = "A",
            right = "E",
            bottom = 1,
            top = 3,
            black = "A1,A2,A3,B1,B3",
            white = "D1,D3,E1,E2,E3",
            goal = Goal.Live,
            targets = "A1,A2,A3,B1,B3",
            leftEdge = EdgeKind.Real,
            bottomEdge = EdgeKind.Real,
        )
        val session = testSession(live)
        assertTrue(session.pass())
        session.waitForIdle()
        assertEquals(PlayStatus.Failure, session.state.value.status)
    }

    @Test
    fun capturingASacrificeDoesNotFailLive() = runTest {
        val session = testSession(Samples.liveCorner)
        assertTrue(session.tryMove(pt("B4")) || session.tryMove(pt("D4")) || session.pass())
        session.waitForIdle()
        if (session.state.value.status == PlayStatus.InProgress) {
            assertTrue(session.pass())
            session.waitForIdle()
        }
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun killRequiresEveryTargetStringDead() = runTest {
        val twoGroups = cornerProblem(
            files = 5,
            ranks = 3,
            black = "A2,B1,C2,D1,E2",
            white = "B2,D2",
            goal = Goal.Kill,
            targets = "B2,D2",
        )
        val session = testSession(twoGroups)
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertNull(session.state.value.stones[pt("B2")])
        assertEquals(StoneColor.White, session.state.value.stones[pt("D2")])
        assertTrue(session.state.value.status == PlayStatus.InProgress || session.state.value.status == PlayStatus.Failure)
        if (session.state.value.status == PlayStatus.InProgress) {
            assertTrue(session.tryMove(pt("D3")))
            session.waitForIdle()
            assertEquals(PlayStatus.Success, session.state.value.status)
        }
    }

    @Test
    fun consecutivePassWhileUnsettledIsFailure() = runTest {
        val session = testSession(
            cornerProblem(
                files = 5,
                ranks = 3,
                black = "",
                white = "A1,A2,A3,B1,B3,C1,C2,D1,D3,E1,E2,E3",
                goal = Goal.Kill,
                targets = "A1,A2,A3,B1,B3,C1,C2,D1,D3,E1,E2,E3",
            ),
        )
        assertTrue(session.pass())
        session.waitForIdle()
        assertEquals(PlayStatus.Failure, session.state.value.status)
    }
}
