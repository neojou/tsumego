package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.classify.firstOwnerMoveToTwoEyes
import com.neojou.tsumego.classify.isAwayFromTargets
import com.neojou.tsumego.classify.minOwnerMovesToTwoEyes
import com.neojou.tsumego.classify.ownerCanForceLife
import com.neojou.tsumego.classify.resolveKos
import com.neojou.tsumego.ScriptedSolver
import com.neojou.tsumego.library.Samples
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
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
    fun afterT16Q15P15P14P18T18T15IsKillSuccessNotWhiteWin() {
        val (pos, log) = playSeq("T16", "Q15", "P15", "P14", "P18")
        val beforeP18 = playSeq("T16", "Q15", "P15", "P14").first
        val away = isAwayFromTargets(beforeP18, pt("P18"), problem.targets)
        val liveAt = firstOwnerMoveToTwoEyes(pos, problem.targets)
        val t18 = pos.play(pt("T18"), StoneColor.White)
        val t18t15 = t18?.play(pt("T15"), StoneColor.Black)
        val dump = log.joinToString("\n") +
            "\nawayP18=$away liveAt=$liveAt afterP18=${classify(pos, problem.targets, false)}" +
            "\nW T18=${t18?.let { classify(it, problem.targets, false) }} canLive=${t18?.let { ownerCanForceLife(it, problem.targets) }}" +
            "\nT18-T15=${t18t15?.let { classify(it, problem.targets, false) }} canLive=${t18t15?.let { ownerCanForceLife(it, problem.targets) }}"
        assertTrue(t18 != null, dump)
        val afterT18 = classify(requireNotNull(t18), problem.targets, bothPassed = false)
        assertTrue(afterT18 != Outcome.Seki, "T18 is not 雙活／白勝\n$dump")
        assertTrue(afterT18 != Outcome.UnconditionalLive, dump)
        val afterT15 = classify(requireNotNull(t18t15), problem.targets, bothPassed = false)
        assertTrue(afterT15 != Outcome.Seki, dump)
        assertTrue(problem.goal.isSuccess(afterT15), "黑 T15 after 白 T18 should 淨殺\n$dump")
        val rest = minOwnerMovesToTwoEyes(requireNotNull(t18), problem.targets)
        assertTrue(rest == null || rest > 0, "T18 is not 一手兩眼, 脫先不可直接反駁\n$dump rest=$rest")
        assertTrue(away, "P18 is 脫先; shortcut used to fire on liveAt=T18\n$dump")
    }

    @Test
    fun afterP18AwayMustNotRefuteAtT18() = runTest(timeout = 3.minutes) {
        val (pos, log) = playSeq("T16", "Q15", "P15", "P14", "P18")
        val paths = ArrayList<String>()
        val result = AlphaBetaSolver().solve(
            SolverInput(
                problem = problem,
                position = pos,
                consecutivePasses = 0,
                budget = UnlimitedBudget,
                lastBlack = pt("P18"),
                blackPlayedAway = true,
                onPath = { paths += it },
            ),
        )
        val dump = log.joinToString("\n") + "\nresult=$result paths=\n${paths.joinToString("\n")}"
        val t18 = Action.Move(pt("T18"))
        assertTrue(
            result !is SolverResult.Refute || result.action != t18,
            "脫先不可把未完成的做活點 T18 當白勝\n$dump",
        )
        assertTrue(
            paths.none { it == "白下 T18 -> 白勝" },
            dump,
        )
    }

    @Test
    fun afterT16WhiteResistsAtT15() = runTest(timeout = 3.minutes) {
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("T16")))
        session.waitForIdle()
        val snap = session.state.value
        val dump =
            "status=${snap.status} last=${snap.lastMove} " +
                "tree=${snap.decisionTree.lines.map { it.text }}"
        assertEquals(PlayStatus.InProgress, snap.status, dump)
        assertEquals(pt("T15"), snap.lastMove, "白應 T15, not Q15\n$dump")
    }

    @Test
    fun afterT16Q15P15P14P18SessionT15Succeeds() = runTest {
        val session = testSession(
            problem,
            solver = ScriptedSolver(listOf(Action.Move(pt("Q15")), Action.Move(pt("P14")), Action.Move(pt("T18")))),
        )
        for (label in listOf("T16", "P15", "P18")) {
            assertTrue(session.tryMove(pt(label)), "illegal $label status=${session.state.value.status} last=${session.state.value.lastMove}")
            session.waitForIdle()
        }
        val afterP18 = session.state.value
        val dumpP18 =
            "after P18 status=${afterP18.status} last=${afterP18.lastMove} tree=${afterP18.decisionTree.lines.map { it.text }}"
        assertTrue(afterP18.status != PlayStatus.Failure, dumpP18)
        assertTrue(session.tryMove(pt("T15")), dumpP18)
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(
            PlayStatus.Success,
            snap.status,
            "after T15 status=${snap.status} last=${snap.lastMove} tree=${snap.decisionTree.lines.map { it.text }}",
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
