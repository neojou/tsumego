package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LdrzSearchTinyTest {
    @Test
    fun captureInAtariIsDeadAndDoesNotUseAnswerFirstMove() {
        val loaded = LdrzJson.parse(TINY_DEAD_JSON, "tiny_dead.json")
        val problem = (loaded as LdrzLoad.Ok).problem
        val result = LdrzSolver().solve(problem)
        assertEquals(LdrzStatus.DEAD, result.status, result.toString())
        assertEquals("bs", result.firstMoveSgf)
        assertEquals("B1", result.firstMove!!.label)
        assertNotEquals(problem.answerFirstMove, result.firstMoveSgf)
        assertTrue(result.numSimulations > 0)
        assertTrue(result.zoneCount >= 1)
    }

    @Test
    fun twoEyesIsAliveWithoutSearchMove() {
        val problem = twoEyeLiveProblem()
        val result = LdrzSolver().solve(problem)
        assertEquals(LdrzStatus.ALIVE, result.status, result.toString())
        assertTrue(result.numSimulations >= 1)
    }

    @Test
    fun missingCrucialIsError() {
        val problem = twoEyeLiveProblem().copy(blackCrucial = emptySet(), whiteCrucial = emptySet())
        val result = LdrzSolver().solve(problem)
        assertEquals(LdrzStatus.ERROR, result.status)
    }
}

private fun twoEyeLiveProblem(): LdrzProblem {
    fun p(label: String) = Point.parseOrThrow(label)
    val black = listOf(
        "A1", "B1", "C1", "D1",
        "A2", "B2", "D2",
        "A3", "C3", "D3",
        "A4", "B4", "C4", "D4",
    ).map { p(it) }
    val region = buildSet {
        for (file in 0..3) {
            for (rank in 1..4) add(Point(file, rank))
        }
    }
    return LdrzProblem(
        stem = "tiny_live",
        sourceJsonName = "tiny_live.json",
        filename = "tiny_live.sgf",
        category = "TOLIVE",
        turnColor = StoneColor.White,
        winningColor = StoneColor.Black,
        blackCrucial = setOf(p("A1")),
        whiteCrucial = emptySet(),
        blackGoal = LdrzGoal.TOLIVE,
        whiteGoal = LdrzGoal.TOKILL,
        answerFirstMove = "aa",
        region = region,
        stones = black.associateWith { StoneColor.Black },
        boardSize = 19,
    )
}
