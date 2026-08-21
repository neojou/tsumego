package com.neojou.tsumego.library

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.lowerLeftCorner
import com.neojou.tsumego.board.upperRightCorner

data class SampleProblem(
    val id: String,
    val name: String,
    val problem: Problem,
)

object Samples {
    val liveCorner: Problem = liveCornerProblem()
    val smallKill: Problem = smallKillProblem()
    val wallAndEdge: Problem = wallAndEdgeProblem()

    val all: List<SampleProblem> = listOf(
        SampleProblem("small-kill", "小殺棋", smallKill),
        SampleProblem("wall-and-edge", "牆與真盤邊", wallAndEdge),
    )

    private fun liveCornerProblem(): Problem {
        val (rect, edges) = lowerLeftCorner(files = 5, ranks = 4)
        val black = labels(
            "A1", "A2", "A3",
            "B1", "B3",
            "C1", "C2",
            "D1", "D3",
            "E1", "E2", "E3",
        )
        val sacrifice = Point.parseOrThrow("C4")
        val stones = (black + sacrifice).associateWith { StoneColor.Black }
        val targets = black.toSet()
        return Problem(rect, edges, stones, Goal.Live, targets)
    }

    private fun smallKillProblem(): Problem {
        val (rect, edges) = lowerLeftCorner(files = 3, ranks = 3)
        val stones = mapOf(
            Point.parseOrThrow("A2") to StoneColor.Black,
            Point.parseOrThrow("B1") to StoneColor.Black,
            Point.parseOrThrow("C2") to StoneColor.Black,
            Point.parseOrThrow("B2") to StoneColor.White,
        )
        return Problem(rect, edges, stones, Goal.Kill, setOf(Point.parseOrThrow("B2")))
    }

    private fun wallAndEdgeProblem(): Problem {
        val (rect, edges) = upperRightCorner(files = 4, ranks = 4)
        val stones = mapOf(
            Point.parseOrThrow("Q18") to StoneColor.Black,
            Point.parseOrThrow("R19") to StoneColor.Black,
            Point.parseOrThrow("S18") to StoneColor.Black,
            Point.parseOrThrow("R18") to StoneColor.White,
        )
        return Problem(rect, edges, stones, Goal.Kill, setOf(Point.parseOrThrow("R18")))
    }

    private fun labels(vararg labels: String): List<Point> = labels.map { Point.parseOrThrow(it) }
}
