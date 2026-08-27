package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.classify.firstOwnerMoveToTwoEyes
import com.neojou.tsumego.classify.ownerCanForceLife
import com.neojou.tsumego.classify.resolveKos
import com.neojou.tsumego.ScriptedSolver
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.actions
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 老鼠偷油：黑錯 T15 後白應 T16 打劫，不是 T18 淨死. */
class Kill7MouseOilResistTest {
    private val problem = Samples.kill7K.withOpenWallMargin()

    @Test
    fun afterT15WhiteResistsAtT16NotT18() = runTest(timeout = 45.seconds) {
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("T15")))
        session.waitForIdle()
        val snap = session.state.value
        val liveAt = firstOwnerMoveToTwoEyes(
            Position.initial(problem).play(pt("T15"), StoneColor.Black)!!,
            problem.targets,
        )
        val dump =
            "status=${snap.status} last=${snap.lastMove} liveAt=$liveAt " +
                "white=${snap.stones.filterValues { it == StoneColor.White }.keys.sorted()}"
        assertNotEquals(PlayStatus.Timeout, snap.status, dump)
        assertEquals(pt("T16"), snap.lastMove, "白應 T16 打劫抵抗, not T18 淨死\n$dump")
    }

    @Test
    fun afterT15WhiteCandidatesIncludeT16BesideLastBlack() {
        val start = Position.initial(problem).play(pt("T15"), StoneColor.Black)!!
        val withLast = actions(start, StoneColor.White, problem, lastBlack = pt("T15"))
            .filterIsInstance<Action.Move>().map { it.point.label }
        val regionOnly = actions(start, StoneColor.White, problem)
            .filterIsInstance<Action.Move>().map { it.point.label }
        assertTrue(pt("T16").label in withLast, "T16 must be a 應手 candidate beside T15, was $withLast")
        assertTrue(pt("T16").label !in regionOnly, "T16 is outside 氣區; lastBlack is load-bearing, region=$regionOnly")
    }

    @Test
    fun t15T16T17S17S16IsKoKillNotKillSuccess() {
        val (pos, log) = playSeq("T15", "T16", "T17", "S17", "S16")
        val dump = log.joinToString("\n")
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertEquals(Outcome.KoKill, outcome, dump)
        assertTrue(!Goal.Kill.isSuccess(outcome), dump)
    }

    @Test
    fun afterT15T16KillFailsWithoutWaitingForMoreBlack() = runTest(timeout = 45.seconds) {
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("T15")))
        session.waitForIdle()
        val snap = session.state.value
        val tree = snap.decisionTree.lines.map { it.text }
        val dump =
            "status=${snap.status} last=${snap.lastMove} tree=\n${tree.joinToString("\n")}"
        assertEquals(pt("T16"), snap.lastMove, dump)
        assertEquals(
            PlayStatus.Failure,
            snap.status,
            "v1 殺棋打劫即失敗，不該再輪黑\n$dump",
        )
        assertTrue(
            tree.any { "白下 T16" in it && "白勝" in it },
            "決策樹 should show 白下 T16 … 白勝\n$dump",
        )
    }

    @Test
    fun t18S17T16T15T17IsSekiKillFailure() {
        val (pos, log) = playSeq("T18", "S17", "T16", "T15", "T17")
        val whiteOn = problem.targets.filter { it in pos.stones }
        val wLibs = whiteOn.flatMap { pos.liberties(pos.stringAt(it)) }.toSet().sorted()
        val adjBlack = wLibs.flatMap { pos.neighbors(it) }
            .filter { pos.stones[it] == StoneColor.Black }.toSet().sorted()
        val bLibs = adjBlack.flatMap { pos.liberties(pos.stringAt(it)) }.toSet().sorted()
        val shared = wLibs.intersect(bLibs.toSet()).sorted()
        val fills = shared.joinToString { p ->
            val asB = pos.play(p, StoneColor.Black)
            val asW = pos.play(p, StoneColor.White)
            val capB = asB?.let { n -> problem.targets.count { t -> t in pos.stones && t !in n.stones } } ?: -1
            val capW = asW?.let { n -> adjBlack.count { t -> t !in n.stones } } ?: -1
            "${p.label} B=${asB != null} capW=$capB W=${asW != null} capB=$capW"
        }
        val dump = log.joinToString("\n") +
            "\nbothPassed=${classify(pos, problem.targets, bothPassed = true)}" +
            "\nwLibs=$wLibs adjBlack=$adjBlack bLibs=$bLibs shared=$shared fills=$fills"
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertEquals(Outcome.Seki, outcome, dump)
        assertTrue(!Goal.Kill.isSuccess(outcome), dump)
    }

    @Test
    fun afterT18S17T16T15T17KillFailsWithoutMorePlay() = runTest {
        val session = testSession(
            problem,
            solver = ScriptedSolver(listOf(Action.Move(pt("S17")), Action.Move(pt("T15")))),
        )
        for (label in listOf("T18", "T16", "T17")) {
            val snap = session.state.value
            assertEquals(
                PlayStatus.InProgress,
                snap.status,
                "before $label status=${snap.status} last=${snap.lastMove}",
            )
            assertTrue(session.tryMove(pt(label)), "illegal $label status=${snap.status} last=${snap.lastMove}")
            session.waitForIdle()
        }
        val snap = session.state.value
        assertEquals(
            PlayStatus.Failure,
            snap.status,
            "v1 殺棋雙活即失敗 status=${snap.status} last=${snap.lastMove}",
        )
    }

    private fun playSeq(vararg labels: String): Pair<Position, List<String>> {
        var pos = Position.initial(problem)
        var color = StoneColor.Black
        val log = ArrayList<String>()
        for (label in labels) {
            pos = assertNotNull(pos.play(pt(label), color), "illegal $label as $color")
            val outcome = classify(pos, problem.targets, bothPassed = false)
            val liveB = ownerCanForceLife(resolveKos(pos, StoneColor.Black), problem.targets)
            val liveW = ownerCanForceLife(resolveKos(pos, StoneColor.White), problem.targets)
            val wOn = problem.targets.filter { it in pos.stones }
            val nWlib = wOn.flatMap { pos.liberties(pos.stringAt(it)) }.toSet().size
            log += "$color $label outcome=$outcome " +
                "canLive=${ownerCanForceLife(pos, problem.targets)} " +
                "liveB=$liveB liveW=$liveW nWlib=$nWlib " +
                "hasKo=${pos.hasKoCandidate()} " +
                "koB=${pos.simpleKoCaptures(StoneColor.Black)} " +
                "koW=${pos.simpleKoCaptures(StoneColor.White)}"
            color = color.opposite
        }
        return pos to log
    }
}
