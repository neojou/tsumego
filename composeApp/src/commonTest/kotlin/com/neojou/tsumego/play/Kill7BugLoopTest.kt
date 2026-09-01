package com.neojou.tsumego.play

import com.neojou.tsumego.KILL_7K_ONE_MORE_JSON
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.classify.firstOwnerMoveToTwoEyes
import com.neojou.tsumego.classify.ownerCanForceLife
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.actions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 7K 黑S17 後 白T17 黑T18 白T19 不是白勝：黑R19 提 T19/S18/S19. */
class Kill7BugLoopTest {
    private val problem = run {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_7K_ONE_MORE_JSON)).problem
        loaded.withOpenWallMargin()
    }

    private fun playSeq(vararg labels: String): Position {
        var pos = Position.initial(problem)
        var color = StoneColor.Black
        for (label in labels) {
            pos = assertNotNull(pos.play(pt(label), color), "illegal $label as $color")
            color = color.opposite
        }
        return pos
    }

    @Test
    fun r19CapturesAfterT17T18T19() {
        val afterT19 = playSeq("S17", "T17", "T18", "T19")
        val outcome = classify(afterT19, problem.targets, bothPassed = false)
        val t18 = afterT19.stones[pt("T18")]
        val s18 = afterT19.stringAt(pt("S18"))
        val dump = "outcome=$outcome t18=$t18 s18=${s18.sorted()} " +
            "s18libs=${afterT19.liberties(s18).sorted()} " +
            "koB=${afterT19.simpleKoCaptures(StoneColor.Black)} " +
            "koW=${afterT19.simpleKoCaptures(StoneColor.White)} " +
            "hasKo=${afterT19.hasKoCandidate()}"
        val captured = assertNotNull(afterT19.play(pt("R19"), StoneColor.Black), "R19 illegal $dump")
        assertTrue(pt("T19") !in captured.stones, "T19 should be captured $dump")
        assertTrue(pt("S18") !in captured.stones, "S18 should be captured $dump")
        assertTrue(pt("S19") !in captured.stones, "S19 should be captured $dump")
        assertEquals(Outcome.Unsettled, outcome, dump)
    }

    @Test
    fun afterS17WhiteT17IsInActionsAndT18NotWhiteWin() {
        val start = playSeq("S17")
        val liveAt = firstOwnerMoveToTwoEyes(start, problem.targets)
        val listedHint = actions(start, StoneColor.White, problem, hintWhite = liveAt)
        val listedLast = actions(start, StoneColor.White, problem, lastBlack = pt("S17"))
        val listedBare = actions(start, StoneColor.White, problem)
        val t17 = start.play(pt("T17"), StoneColor.White)
        val t17t18 = t17?.play(pt("T18"), StoneColor.Black)
        fun labs(list: List<Action>) = list.map { if (it is Action.Move) it.point.label else "Pass" }
        val dump = buildString {
            appendLine("liveAt=$liveAt")
            appendLine("bare=${labs(listedBare)}")
            appendLine("hint=${labs(listedHint)}")
            appendLine("last=${labs(listedLast)}")
            appendLine("T17legal=${t17 != null} afterT17=${t17?.let { classify(it, problem.targets, false) }} canLive=${t17?.let { ownerCanForceLife(it, problem.targets) }}")
            appendLine("T17T18=${t17t18?.let { classify(it, problem.targets, false) }} T17on=${t17t18?.stones?.get(pt("T17"))}")
        }
        assertTrue(
            listedLast.any { it is Action.Move && it.point == pt("T17") },
            "T17 is 黑 S17 的鄰空, Session lastBlack must keep it\n$dump",
        )
        assertNotNull(t17, dump)
        val afterT18 = classify(requireNotNull(t17t18), problem.targets, bothPassed = false)
        assertTrue(afterT18 != Outcome.Seki, dump)
        assertTrue(afterT18 != Outcome.UnconditionalLive, dump)
        assertTrue(afterT18 == Outcome.Unsettled || afterT18 == Outcome.UnconditionalDead, dump)
    }

    @Test
    fun afterS17T17T18IsUnsettledNotSeki() {
        val pos = playSeq("S17", "T17", "T18")
        val top = pos.stringAt(pt("S18"))
        val afterR19 = pos.play(pt("R19"), StoneColor.Black)
        val captured = afterR19?.play(pt("T19"), StoneColor.Black)
        val dump =
            "outcome=${classify(pos, problem.targets, false)} " +
                "T17=${pos.stones[pt("T17")]} T18=${pos.stones[pt("T18")]} S17=${pos.stones[pt("S17")]} " +
                "s18libs=${pos.liberties(top).sorted()} " +
                "afterR19T19 s18=${captured?.stones?.get(pt("S18"))} s19=${captured?.stones?.get(pt("S19"))}"
        assertEquals(null, pos.stones[pt("T17")], "T18 captures T17\n$dump")
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertTrue(outcome != Outcome.Seki, "not 雙活／白勝\n$dump")
        assertTrue(
            outcome == Outcome.Unsettled || outcome == Outcome.UnconditionalDead,
            dump,
        )
        assertTrue(pt("S18") !in requireNotNull(captured).stones, "R19 then T19 captures S18–S19\n$dump")
    }
}
