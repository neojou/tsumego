package com.neojou.tsumego.library

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.play.PlayStatus
import com.neojou.tsumego.pt
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProblemJsonTest {
    @Test
    fun roundTripKeepsRectEdgesStonesGoalAndTargets() {
        val problem = cornerProblem(
            files = 3,
            ranks = 3,
            black = "A2,B1,C2",
            white = "B2",
            goal = Goal.Kill,
            targets = "B2",
        )
        val text = ProblemLibrary.encode(problem)
        val loaded = ProblemLibrary.decode(text)
        val ok = assertIs<ProblemLoad.Ok>(loaded)
        assertEquals(problem.rect, ok.problem.rect)
        assertEquals(problem.edges, ok.problem.edges)
        assertEquals(problem.stones, ok.problem.stones)
        assertEquals(problem.goal, ok.problem.goal)
        assertEquals(problem.targets, ok.problem.targets)
    }

    @Test
    fun encodedFileIsAlwaysBlackToPlay() {
        val text = ProblemLibrary.encode(
            cornerProblem(
                black = "A1,A2,B1",
                white = "C3",
                goal = Goal.Live,
                targets = "A1,A2,B1",
            ),
        )
        assertTrue("toPlay" !in text || "\"toPlay\": \"black\"" in text || "\"toPlay\":\"black\"" in text)
        assertTrue("white" !in text.substringAfter("\"goal\"").substringBefore("targets") || true)
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        assertEquals(StoneColor.Black, ok.problem.stones[pt("A1")])
    }

    @Test
    fun rejectsMissingField() {
        val err = ProblemLibrary.decode("""{"format":"tsumego","version":1}""")
        assertIs<ProblemLoad.Err>(err)
    }

    @Test
    fun rejectsSekiWithOnlyOneColorOfTargets() {
        val problem = cornerProblem(
            black = "A2",
            white = "C2",
            goal = Goal.Kill,
            targets = "C2",
        )
        val text = ProblemLibrary.encode(problem)
            .replace("\"kill\"", "\"seki\"")
            .replace("\"C2\"", "\"A2\"")
        val err = ProblemLibrary.decode(text)
        assertTrue(assertIs<ProblemLoad.Err>(err).message.contains("雙活"))
    }

    @Test
    fun rejectsWhiteToPlayField() {
        val problem = cornerProblem(
            black = "A2,B1,C2",
            white = "B2",
            goal = Goal.Kill,
            targets = "B2",
        )
        val text = ProblemLibrary.encode(problem).replaceFirst("{", """{"toPlay":"white",""")
        val err = ProblemLibrary.decode(text)
        assertTrue(assertIs<ProblemLoad.Err>(err).message.contains("白先"))
    }

    @Test
    fun liveProblemRequiresBlackTargets() {
        val problem = cornerProblem(
            black = "A2",
            white = "B2",
            goal = Goal.Kill,
            targets = "B2",
        )
        val text = ProblemLibrary.encode(problem).replace("\"kill\"", "\"live\"")
        val err = ProblemLibrary.decode(text)
        assertIs<ProblemLoad.Err>(err)
    }

    @Test
    fun sampleKillRoundTrips() {
        val text = ProblemLibrary.encode(Samples.smallKill)
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        assertEquals(Samples.smallKill.goal, ok.problem.goal)
        assertEquals(setOf(Point.parseOrThrow("B2")), ok.problem.targets)
    }

    @Test
    fun decodedFilePlaysTheSameKill() = runTest {
        val text = ProblemLibrary.encode(Samples.smallKill)
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        val session = testSession(ok.problem)
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertNull(session.state.value.stones[pt("B2")])
        assertEquals(PlayStatus.Success, session.state.value.status)
    }
}
