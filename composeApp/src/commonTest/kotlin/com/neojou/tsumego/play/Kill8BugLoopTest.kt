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
import com.neojou.tsumego.solve.actions
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
    fun afterS17S19T18IsKillSuccessNotWhiteWin() {
        var pos = Position.initial(problem)
        val log = ArrayList<String>()
        val plays = listOf(
            StoneColor.Black to "S17",
            StoneColor.White to "S19",
            StoneColor.Black to "T18",
        )
        for ((color, label) in plays) {
            pos = assertNotNull(pos.play(pt(label), color), dump(pos, "illegal $color $label"))
            val wOn = problem.targets.filter { it in pos.stones }
            val wLibs = wOn.flatMap { pos.liberties(pos.stringAt(it)) }.toSet().sorted()
            val adjBlack = wLibs.flatMap { pos.neighbors(it) }
                .filter { pos.stones[it] == StoneColor.Black }.toSet()
            val shared = wLibs.intersect(adjBlack.flatMap { pos.liberties(pos.stringAt(it)) }.toSet()).sorted()
            log += dump(pos, "$color $label") + " wLibs=$wLibs shared=$shared"
        }
        val dump = log.joinToString("\n")
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertTrue(outcome != Outcome.Seki, "T18 is 彎三／點眼, not 雙活／白勝\n$dump")
        assertTrue(outcome != Outcome.KoKill, dump)
        assertTrue(outcome != Outcome.UnconditionalLive, dump)
        assertTrue(problem.goal.isSuccess(outcome), "after T18 black 彎三, white cannot 兩眼\n$dump")
    }

    @Test
    fun afterS17S19T18SessionSucceeds() = runTest {
        val session = testSession(
            problem,
            solver = ScriptedSolver(listOf(Action.Move(pt("S19")))),
        )
        assertTrue(session.tryMove(pt("S17")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("T18")), "after S17 status=${session.state.value.status} last=${session.state.value.lastMove}")
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(
            PlayStatus.Success,
            snap.status,
            "status=${snap.status} last=${snap.lastMove} tree=${snap.decisionTree.lines.map { it.text }}",
        )
    }

    @Test
    fun afterS18S19T18IsDeadNotSeki() {
        var pos = Position.initial(problem)
        pos = requireNotNull(pos.play(pt("S18"), StoneColor.Black))
        pos = requireNotNull(pos.play(pt("S19"), StoneColor.White))
        pos = requireNotNull(pos.play(pt("T18"), StoneColor.Black))
        val outcome = classify(pos, problem.targets, bothPassed = false)
        val dump = dump(pos, "S18-S19-T18")
        assertTrue(outcome != Outcome.Seki, dump)
        assertEquals(Outcome.UnconditionalDead, outcome, dump)
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
    fun afterT18S18T17IsKoFightNotKillSuccess() {
        var pos = Position.initial(problem)
        val log = ArrayList<String>()
        for ((color, label) in listOf(
            StoneColor.Black to "T18",
            StoneColor.White to "S18",
            StoneColor.Black to "T17",
        )) {
            pos = assertNotNull(pos.play(pt(label), color), dump(pos, "illegal $color $label"))
            log += dump(pos, "$color $label") +
                " hasKo=${pos.hasKoCandidate()} koB=${pos.simpleKoCaptures(StoneColor.Black)} koW=${pos.simpleKoCaptures(StoneColor.White)}"
        }
        val t19 = pos.play(pt("T19"), StoneColor.White)
        val t19s19 = t19?.play(pt("S19"), StoneColor.Black)
        log += "T19=${t19?.let { dump(it, "") }} hasKo=${t19?.hasKoCandidate()} koB=${t19?.simpleKoCaptures(StoneColor.Black)}"
        log += "T19-S19=${t19s19?.let { dump(it, "") }}"
        val dump = log.joinToString("\n")
        val afterT17 = classify(pos, problem.targets, bothPassed = false)
        assertTrue(afterT17 != Outcome.UnconditionalDead, "T17 is 打劫未成，不是淨殺\n$dump")
        assertTrue(!problem.goal.isSuccess(afterT17), dump)
        val afterT19 = classify(requireNotNull(t19), problem.targets, bothPassed = false)
        assertTrue(afterT19 != Outcome.UnconditionalDead, "白 T19 後黑只能 S19 打劫，不是黑勝\n$dump")
        assertTrue(!problem.goal.isSuccess(afterT19), dump)
        val afterS19 = classify(requireNotNull(t19s19), problem.targets, bothPassed = false)
        assertEquals(Outcome.KoKill, afterS19, dump)
        assertTrue(!problem.goal.isSuccess(afterS19), dump)
        val listed = actions(pos, StoneColor.White, problem, lastBlack = pt("T17"))
        val labels = listed.map { if (it is Action.Move) it.point.label else "Pass" }
        assertTrue(pt("T19").label in labels, "T19 打劫必須在白候選手裡, was $labels\n$dump")
    }

    @Test
    fun afterT18WhiteResistsAtS18NotS19() = runTest(timeout = 3.minutes) {
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("T18")))
        session.waitForIdle()
        val snap = session.state.value
        val dump =
            "status=${snap.status} last=${snap.lastMove} tree=${snap.decisionTree.lines.map { it.text }}"
        assertEquals(pt("S18"), snap.lastMove, "白應 S18 不是 S19\n$dump")
        assertTrue(
            snap.decisionTree.lines.any { "白下 T19" in it.text && "白勝" in it.text } ||
                snap.searchPaths.any { it.contains("白下 S18") && it.contains("黑下 T17") && it.contains("白下 T19") && it.endsWith("白勝") },
            "最長抵抗續線應是 S18–T17–T19 打劫白勝, not S17 黑勝\n$dump paths=${snap.searchPaths.takeLast(12)}",
        )
    }

    @Test
    fun afterT18S18T17SessionIsNotSuccess() = runTest {
        val session = testSession(
            problem,
            solver = ScriptedSolver(listOf(Action.Move(pt("S18")), Action.Move(pt("T19")))),
        )
        assertTrue(session.tryMove(pt("T18")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("T17")), "after T18 status=${session.state.value.status} last=${session.state.value.lastMove}")
        session.waitForIdle()
        val afterT17 = session.state.value
        val dumpT17 = "after T17 status=${afterT17.status} last=${afterT17.lastMove} tree=${afterT17.decisionTree.lines.map { it.text }}"
        assertTrue(afterT17.status != PlayStatus.Success, dumpT17)
        assertTrue(session.tryMove(pt("S19")), dumpT17)
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(
            PlayStatus.Failure,
            snap.status,
            "S19 打劫 v1 失敗 status=${snap.status} last=${snap.lastMove}",
        )
    }

    @Test
    fun koTakeAfterT19S19IsKoKillNotUnconditionalDead() {
        var pos = Position.initial(problem)
        for ((color, label) in listOf(
            StoneColor.Black to "T18",
            StoneColor.White to "S18",
            StoneColor.Black to "T17",
            StoneColor.White to "T19",
            StoneColor.Black to "S19",
        )) {
            pos = assertNotNull(pos.play(pt(label), color), dump(pos, "illegal $color $label"))
        }
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertEquals(Outcome.KoKill, outcome, dump(pos, "ko take"))
        assertTrue(!problem.goal.isSuccess(outcome), dump(pos, "ko take"))
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
