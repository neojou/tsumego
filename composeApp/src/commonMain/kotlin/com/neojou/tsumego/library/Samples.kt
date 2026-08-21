package com.neojou.tsumego.library

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.lowerLeftCorner

data class SampleProblem(
    val id: String,
    val name: String,
    val problem: Problem,
)

object Samples {
    val liveCorner: Problem = liveCornerProblem()
    val smallKill: Problem = smallKillProblem()
    val kill15K: Problem = loadKill(KILL_15K_JSON)
    val kill13K: Problem = loadKill(KILL_13K_JSON)

    val all: List<SampleProblem> = listOf(
        SampleProblem("15k-kill", "15K 殺棋", kill15K),
        SampleProblem("13k-kill", "13K 殺棋", kill13K),
    )

    private fun loadKill(json: String): Problem {
        val loaded = ProblemLibrary.decode(json)
        check(loaded is ProblemLoad.Ok) { (loaded as ProblemLoad.Err).message }
        return loaded.problem
    }

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

    private fun labels(vararg labels: String): List<Point> = labels.map { Point.parseOrThrow(it) }
}

private const val KILL_15K_JSON = """
{
    "format": "tsumego",
    "version": 1,
    "rect": {
        "left": "P",
        "right": "T",
        "bottom": 15,
        "top": 19
    },
    "edges": {
        "left": "wall",
        "right": "real",
        "bottom": "wall",
        "top": "real"
    },
    "stones": {
        "Q16": "black",
        "Q17": "black",
        "Q18": "black",
        "Q19": "black",
        "R16": "black",
        "R17": "white",
        "R18": "white",
        "R19": "white",
        "S16": "black",
        "S17": "white",
        "T16": "black",
        "T17": "white",
        "T19": "white"
    },
    "goal": "kill",
    "targets": [
        "R17",
        "R18",
        "R19",
        "S17",
        "T17",
        "T19"
    ]
}
"""

private const val KILL_13K_JSON = """
{
    "format": "tsumego",
    "version": 1,
    "rect": {
        "left": "O",
        "right": "T",
        "bottom": 15,
        "top": 19
    },
    "edges": {
        "left": "wall",
        "right": "real",
        "bottom": "wall",
        "top": "real"
    },
    "stones": {
        "P16": "black",
        "P17": "black",
        "P18": "black",
        "Q16": "black",
        "Q17": "white",
        "Q18": "white",
        "R16": "black",
        "R17": "white",
        "S16": "black",
        "S17": "white",
        "S18": "white",
        "T16": "black",
        "T17": "black",
        "T18": "white"
    },
    "goal": "kill",
    "targets": [
        "Q17",
        "Q18",
        "R17",
        "S17",
        "S18",
        "T18"
    ]
}
"""
