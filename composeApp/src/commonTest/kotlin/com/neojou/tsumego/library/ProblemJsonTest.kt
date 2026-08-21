package com.neojou.tsumego.library

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.play.PlayStatus
import com.neojou.tsumego.play.playHeading
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
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
        )
        assertTrue("toPlay" !in text || "\"toPlay\": \"black\"" in text || "\"toPlay\":\"black\"" in text)
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        assertEquals(StoneColor.Black, ok.problem.stones[pt("A2")])
    }

    @Test
    fun rejectsMissingField() {
        val err = ProblemLibrary.decode("""{"format":"tsumego","version":1}""")
        assertIs<ProblemLoad.Err>(err)
    }

    @Test
    fun rejectsNonKillGoalSeki() {
        val problem = cornerProblem(
            black = "A2",
            white = "C2",
            goal = Goal.Kill,
            targets = "C2",
        )
        val text = ProblemLibrary.encode(problem).replace("\"kill\"", "\"seki\"")
        val err = ProblemLibrary.decode(text)
        val message = assertIs<ProblemLoad.Err>(err).message
        assertTrue(message.contains("題型") && message.contains("殺棋"), message)
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
    fun rejectsNonKillGoalLive() {
        val problem = cornerProblem(
            black = "A2",
            white = "B2",
            goal = Goal.Kill,
            targets = "B2",
        )
        val text = ProblemLibrary.encode(problem).replace("\"kill\"", "\"live\"")
        val err = ProblemLibrary.decode(text)
        val message = assertIs<ProblemLoad.Err>(err).message
        assertTrue(message.contains("題型") && message.contains("殺棋"), message)
    }

    @Test
    fun rejectsNonKillGoalKoLiveAndKoKill() {
        val kill = ProblemLibrary.encode(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
        )
        for (goal in listOf("koLive", "koKill")) {
            val err = ProblemLibrary.decode(kill.replace("\"kill\"", "\"$goal\""))
            val message = assertIs<ProblemLoad.Err>(err).message
            assertTrue(message.contains("題型") && message.contains("殺棋"), "$goal -> $message")
        }
    }

    @Test
    fun decodedKillHeadingIsBlackFirstKillWhite() {
        val text = ProblemLibrary.encode(Samples.smallKill)
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        assertEquals(Goal.Kill, ok.problem.goal)
        assertEquals("黑先殺白", ok.problem.goal.playHeading())
    }

    @Test
    fun listedSamplesAreKillOnly() {
        assertTrue(Samples.all.none { "做活" in it.name })
        assertTrue(Samples.all.all { it.problem.goal == Goal.Kill })
        assertTrue(Samples.all.any { it.name == "小殺棋" })
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
