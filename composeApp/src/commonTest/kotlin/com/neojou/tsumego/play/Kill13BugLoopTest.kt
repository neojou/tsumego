package com.neojou.tsumego.play

import com.neojou.tsumego.ScriptedSolver
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.classify.ownerCanForceLife
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 13K：黑 R19 白 Q19 黑 S19 應淨殺成功，不是樹上 白勝／對局失敗. */
class Kill13BugLoopTest {
    private val problem = Samples.kill13K.withOpenWallMargin()

    @Test
    fun afterR19Q19S19IsKillSuccessNotWhiteWin() {
        var pos = Position.initial(problem)
        val plays = listOf(
            StoneColor.Black to "R19",
            StoneColor.White to "Q19",
            StoneColor.Black to "S19",
        )
        val log = ArrayList<String>()
        for ((color, label) in plays) {
            pos = assertNotNull(pos.play(pt(label), color), "illegal $color $label")
            val onBoard = problem.targets.filter { it in pos.stones }.sorted()
            val outcome = classify(pos, problem.targets, bothPassed = false)
            val wOn = problem.targets.filter { it in pos.stones }
            val wLibs = wOn.flatMap { pos.liberties(pos.stringAt(it)) }.toSet().sorted()
            val adjBlack = wLibs.flatMap { pos.neighbors(it) }
                .filter { pos.stones[it] == StoneColor.Black }.toSet()
            val shared = wLibs.intersect(adjBlack.flatMap { pos.liberties(pos.stringAt(it)) }.toSet()).sorted()
            val strings = wOn.distinctBy { pos.stringAt(it) }.map {
                val s = pos.stringAt(it)
                "${s.sorted()} libs=${pos.liberties(s).sorted()}"
            }
            log += "$color $label outcome=$outcome canLive=${ownerCanForceLife(pos, problem.targets)} " +
                "onBoard=$onBoard strings=$strings wLibs=$wLibs shared=$shared " +
                "hasKo=${pos.hasKoCandidate()} koB=${pos.simpleKoCaptures(StoneColor.Black)}"
        }
        val replyDump = listOf("T19", "R18").joinToString { label ->
            val next = pos.play(pt(label), StoneColor.Black)
            if (next == null) "$label=illegal" else {
                "$label o=${classify(next, problem.targets, false)} " +
                    "canLive=${ownerCanForceLife(next, problem.targets)}"
            }
        }
        val dump = log.joinToString("\n") + "\nreplies=$replyDump"
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertTrue(outcome != Outcome.Seki, "S19 should not freeze as 雙活\n$dump")
        assertTrue(outcome != Outcome.KoKill, "S19 should not freeze as 劫殺／白勝\n$dump")
        assertEquals(Outcome.UnconditionalDead, outcome, dump)
        assertTrue(problem.goal.isSuccess(outcome), "S19 should 淨殺成功\n$dump")
    }

    @Test
    fun afterR19Q19StillUnsettledUntilS19() {
        var pos = Position.initial(problem)
        pos = requireNotNull(pos.play(pt("R19"), StoneColor.Black))
        pos = requireNotNull(pos.play(pt("Q19"), StoneColor.White))
        assertEquals(
            Outcome.Unsettled,
            classify(pos, problem.targets, bothPassed = false),
            "點眼前還未死，白 Q19 不是假 黑勝",
        )
    }

    @Test
    fun afterR19Q19S19SessionSucceeds() = runTest {
        val session = testSession(
            problem,
            solver = ScriptedSolver(listOf(Action.Move(pt("Q19")))),
        )
        assertTrue(session.tryMove(pt("R19")))
        session.waitForIdle()
        assertTrue(session.tryMove(pt("S19")), "S19 illegal status=${session.state.value.status}")
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(
            PlayStatus.Success,
            snap.status,
            "status=${snap.status} last=${snap.lastMove} tree=${snap.decisionTree.lines.map { it.text }}",
        )
    }
}
