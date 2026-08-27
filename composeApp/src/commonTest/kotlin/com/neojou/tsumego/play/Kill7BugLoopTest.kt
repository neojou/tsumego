package com.neojou.tsumego.play

import com.neojou.tsumego.KILL_7K_ONE_MORE_JSON
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
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
}
