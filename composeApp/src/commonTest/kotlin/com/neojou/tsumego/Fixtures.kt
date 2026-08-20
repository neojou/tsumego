package com.neojou.tsumego

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.lowerLeftCorner
import com.neojou.tsumego.play.Session
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.Budget
import com.neojou.tsumego.solve.Solver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.SolverResult
import com.neojou.tsumego.solve.UnlimitedBudget
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
    budget: Budget = UnlimitedBudget,
): Session = Session(
    problem = problem,
    solver = solver,
    scope = this,
    searchDispatcher = Dispatchers.Unconfined,
    newBudget = { budget },
)

object AlwaysPassSolver : Solver {
    override fun solve(input: SolverInput): SolverResult = SolverResult.Resist(Action.Pass)
}

object ImmediateTimeoutSolver : Solver {
    override fun solve(input: SolverInput): SolverResult = SolverResult.Timeout
}

class ScriptedSolver(private val replies: List<Action>) : Solver {
    private var index = 0
    override fun solve(input: SolverInput): SolverResult {
        val action = replies.getOrElse(index) { Action.Pass }
        index++
        return SolverResult.Resist(action)
    }
}
