package com.neojou.tsumego

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.lowerLeftCorner
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.play.Session
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.Solver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.SolverResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun pt(label: String): Point = Point.parseOrThrow(label)

fun stones(black: String = "", white: String = ""): Map<Point, StoneColor> = buildMap {
    if (black.isNotBlank()) {
        black.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            put(pt(it), StoneColor.Black)
        }
    }
    if (white.isNotBlank()) {
        white.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            put(pt(it), StoneColor.White)
        }
    }
}

fun targets(labels: String): Set<Point> =
    labels.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { pt(it) }.toSet()

fun cornerProblem(
    files: Int = 3,
    ranks: Int = 3,
    black: String = "",
    white: String = "",
    goal: Goal,
    targets: String,
): Problem {
    val (rect, edges) = lowerLeftCorner(files, ranks)
    return Problem(rect, edges, stones(black, white), goal, targets(targets))
}

/**
 * Kill problem whose 開局白活點 is C5 (straight-three vital: two eyes B5/D5).
 * Black A1 is a decoy in atari so that after black plays B5, capturing at A2
 * is a smaller-coordinate capture than C5 — without the hint, A2 would be first.
 */
fun openingWhiteLifeProblem(): Problem = boxedProblem(
    left = "A",
    right = "D",
    bottom = 1,
    top = 6,
    black = "A1",
    white = "A3,A4,A5,A6,B1,B4,B6,C4,C6,D4,D6",
    goal = Goal.Kill,
    targets = "A3,A4,A5,A6,B4,B6,C4,C6,D4,D6",
)

fun boxedProblem(
    left: String,
    right: String,
    bottom: Int,
    top: Int,
    black: String = "",
    white: String = "",
    goal: Goal,
    targets: String,
    leftEdge: EdgeKind = EdgeKind.Wall,
    rightEdge: EdgeKind = EdgeKind.Wall,
    bottomEdge: EdgeKind = EdgeKind.Wall,
    topEdge: EdgeKind = EdgeKind.Wall,
): Problem {
    val rect = BoardRect(
        left = Point.fileIndex(left[0])!!,
        right = Point.fileIndex(right[0])!!,
        bottom = bottom,
        top = top,
    )
    val edges = Edges(leftEdge, rightEdge, bottomEdge, topEdge)
    return Problem(rect, edges, stones(black, white), goal, targets(targets))
}

fun CoroutineScope.testSession(
    problem: Problem,
    solver: Solver = AlwaysPassSolver,
): Session = Session(
    problem = problem,
    solver = solver,
    scope = this,
    searchDispatcher = Dispatchers.Unconfined,
)

object AlwaysPassSolver : Solver {
    override suspend fun solve(input: SolverInput): SolverResult = SolverResult.Resist(Action.Pass)
}

const val SMALL_TRICK_JSON = """
{
    "format": "tsumego",
    "version": 1,
    "rect": { "left": "O", "right": "T", "bottom": 14, "top": 19 },
    "edges": { "left": "wall", "right": "real", "bottom": "wall", "top": "real" },
    "stones": {
        "P15": "black", "Q17": "black", "Q18": "black", "Q19": "black",
        "R14": "black", "R15": "black", "R16": "black",
        "R17": "white", "R18": "white", "S16": "white", "T18": "white"
    },
    "goal": "kill",
    "targets": ["R17", "R18", "S16", "T18"]
}
"""

fun smallTrickPlayable(): Problem {
    val loaded = ProblemLibrary.decode(SMALL_TRICK_JSON)
    check(loaded is ProblemLoad.Ok) { loaded.toString() }
    return loaded.problem
        .copy(targets = setOf(pt("R17"), pt("R18"), pt("T18")))
        .withOpenWallMargin()
}

object ImmediateTimeoutSolver : Solver {
    override suspend fun solve(input: SolverInput): SolverResult = SolverResult.Timeout
}

class ScriptedSolver(private val replies: List<Action>) : Solver {
    private var index = 0
    override suspend fun solve(input: SolverInput): SolverResult {
        val action = replies.getOrElse(index) { Action.Pass }
        index++
        return SolverResult.Resist(action)
    }
}
