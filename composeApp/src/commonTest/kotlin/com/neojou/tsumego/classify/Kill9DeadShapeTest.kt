package com.neojou.tsumego.classify

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 9K-kill-20260828：黑 Q19 提五子後，剩餘白做不成兩眼應立刻無條件死。 */
class Kill9DeadShapeTest {
    private val problem = run {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_9K_JSON)).problem
        loaded.withOpenWallMargin()
    }

    @Test
    fun afterQ19CaptureRemainingWhiteCannotMakeTwoEyes() {
        var pos = Position.initial(problem)
        val plays = listOf(
            StoneColor.Black to "P18",
            StoneColor.White to "P17",
            StoneColor.Black to "Q17",
            StoneColor.White to "Q18",
            StoneColor.Black to "Q16",
            StoneColor.White to "P19",
            StoneColor.Black to "Q19",
        )
        val log = ArrayList<String>()
        for ((color, label) in plays) {
            val beforeWhite = pos.stones.filterValues { it == StoneColor.White }.keys.sorted()
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
            val afterWhite = pos.stones.filterValues { it == StoneColor.White }.keys.sorted()
            val captured = beforeWhite.filter { it !in afterWhite }
            log += "$color $label captured=$captured white=$afterWhite"
        }
        val onBoard = problem.targets.filter { it in pos.stones }.sorted()
        val canLive = ownerCanForceLife(pos, problem.targets)
        val outcome = classify(pos, problem.targets, bothPassed = false)
        val dump = log.joinToString("\n") + "\nonBoard=$onBoard canLive=$canLive outcome=$outcome"
        assertTrue(onBoard.isNotEmpty(), "expected remaining 白\n$dump")
        assertTrue(!canLive, "remaining 白 cannot make two eyes even if black 脫先\n$dump")
        assertEquals(Outcome.UnconditionalDead, outcome, dump)
        assertTrue(problem.goal.isSuccess(outcome), dump)
    }
}

private const val KILL_9K_JSON = """
{
    "format": "tsumego",
    "version": 1,
    "rect": { "left": "K", "right": "T", "bottom": 14, "top": 19 },
    "edges": { "left": "wall", "right": "real", "bottom": "wall", "top": "real" },
    "stones": {
        "L17": "black", "L18": "black",
        "M16": "black", "M18": "white", "M19": "white",
        "N16": "black", "N17": "white",
        "O16": "black", "O17": "white", "O19": "white",
        "P16": "black",
        "Q15": "black",
        "R15": "black", "R16": "white", "R17": "white", "R18": "white", "R19": "white",
        "S16": "black", "S17": "black", "S18": "black", "S19": "black"
    },
    "goal": "kill",
    "targets": ["M18", "M19", "N17", "O17", "O19", "R16", "R17", "R18", "R19"]
}
"""
