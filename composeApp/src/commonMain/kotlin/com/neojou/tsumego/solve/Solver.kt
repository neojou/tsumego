package com.neojou.tsumego.solve

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

fun interface Budget {
    fun expired(): Boolean
}

object UnlimitedBudget : Budget {
    override fun expired(): Boolean = false
}

fun timeBudget(limitMs: Long, timeSource: TimeSource = TimeSource.Monotonic): Budget {
    val deadline = timeSource.markNow() + limitMs.milliseconds
    return Budget { deadline.hasPassedNow() }
}

sealed class Action {
    data class Move(val point: Point) : Action()
    data object Pass : Action()
}

sealed class SolverResult {
    data class Resist(val action: Action) : SolverResult()
    data class Refute(val action: Action) : SolverResult()
    data object Timeout : SolverResult()
}

data class SolverInput(
    val problem: Problem,
    val position: Position,
    val consecutivePasses: Int,
    val budget: Budget,
)

fun interface Solver {
    fun solve(input: SolverInput): SolverResult
}

private sealed class Force {
    data class Yes(val proofPly: Int, val nodes: Int) : Force()
    data class No(val nodes: Int) : Force()
    data object Unknown : Force()
    data object TimedOut : Force()
}

class AlphaBetaSolver(
    private val maxDepth: Int = 48,
) : Solver {
    override fun solve(input: SolverInput): SolverResult {
        if (input.budget.expired()) return SolverResult.Timeout
        val search = Search(input.problem, input.budget)
        var proven: Force? = null
        var provenDepth = 0
        for (depth in 1..maxDepth) {
            if (input.budget.expired()) return SolverResult.Timeout
            val result = search.canForce(
                position = input.position,
                toPlay = StoneColor.White,
                passes = input.consecutivePasses,
                depth = depth,
            )
            when (result) {
                is Force.TimedOut -> return SolverResult.Timeout
                is Force.Yes, is Force.No -> {
                    proven = result
                    provenDepth = depth
                    break
                }
                Force.Unknown -> Unit
            }
        }
        return when (val p = proven) {
            null, Force.Unknown -> SolverResult.Timeout
            is Force.TimedOut -> SolverResult.Timeout
            is Force.Yes -> search.pickResist(input, provenDepth)
            is Force.No -> search.pickRefute(input, provenDepth)
        }
    }
}

private class Search(
    private val problem: Problem,
    private val budget: Budget,
) {
    private val proven = HashMap<String, Force>()
    private val outcomes = HashMap<String, Outcome>()
    private val nodes = IntArray(1)

    fun pickResist(input: SolverInput, proveDepth: Int): SolverResult {
        val scored = ArrayList<Triple<Action, Int, Int>>()
        for (action in actions(input.position, StoneColor.White, problem.targets)) {
            if (budget.expired()) return SolverResult.Timeout
            val next = applyAction(input.position, action, StoneColor.White, input.consecutivePasses) ?: continue
            var found: Force.Yes? = null
            for (depth in 1..proveDepth) {
                val child = canForce(next.first, StoneColor.Black, next.second, depth)
                when (child) {
                    is Force.TimedOut -> return SolverResult.Timeout
                    is Force.Yes -> {
                        found = child
                        break
                    }
                    is Force.No -> break
                    Force.Unknown -> Unit
                }
            }
            if (found != null) scored += Triple(action, found.proofPly, found.nodes)
        }
        if (scored.isEmpty()) return SolverResult.Resist(Action.Pass)
        val best = scored.maxWith(
            compareBy<Triple<Action, Int, Int>> { it.second }
                .thenBy { if (it.first is Action.Pass) 0 else 1 }
                .thenBy { it.third }
                .thenBy { -actionRank(it.first) },
        )
        return SolverResult.Resist(best.first)
    }

    fun pickRefute(input: SolverInput, proveDepth: Int): SolverResult {
        val preventing = ArrayList<Action>()
        for (action in actions(input.position, StoneColor.White, problem.targets)) {
            if (budget.expired()) return SolverResult.Timeout
            val next = applyAction(input.position, action, StoneColor.White, input.consecutivePasses) ?: continue
            var child: Force = Force.Unknown
            for (depth in 1..proveDepth) {
                child = canForce(next.first, StoneColor.Black, next.second, depth)
                if (child is Force.TimedOut) return SolverResult.Timeout
                if (child is Force.Yes || child is Force.No) break
            }
            if (child is Force.No) preventing += action
        }
        if (preventing.isEmpty()) return SolverResult.Refute(Action.Pass)
        val stones = preventing.filterIsInstance<Action.Move>().sortedBy { it.point }
        return SolverResult.Refute(stones.firstOrNull() ?: Action.Pass)
    }

    fun canForce(
        position: Position,
        toPlay: StoneColor,
        passes: Int,
        depth: Int,
    ): Force {
        nodes[0]++
        if ((nodes[0] and 7) == 0 && budget.expired()) return Force.TimedOut

        val key = "${position.key}|${toPlay.name}|$passes|${position.past.sorted().joinToString()}"
        proven[key]?.let { return it }

        val bothPassed = passes >= 2
        val outcome = cachedOutcome(position, bothPassed)
        val terminal = when {
            problem.goal.isSuccess(outcome) -> Force.Yes(proofPly = 0, nodes = 1)
            outcome != Outcome.Unsettled || bothPassed -> Force.No(nodes = 1)
            else -> null
        }
        if (terminal != null) {
            proven[key] = terminal
            return terminal
        }
        if (depth <= 0) return Force.Unknown

        val moves = actions(position, toPlay, problem.targets)
        val result = if (toPlay == StoneColor.Black) {
            orMoves(position, passes, depth, moves)
        } else {
            andMoves(position, passes, depth, moves)
        }
        if (result is Force.Yes || result is Force.No) proven[key] = result
        return result
    }

    private fun orMoves(
        position: Position,
        passes: Int,
        depth: Int,
        moves: List<Action>,
    ): Force {
        var anyUnknown = false
        var sawTimeout = false
        var totalNodes = 0
        var allNo = true
        for (action in moves) {
            val next = applyAction(position, action, StoneColor.Black, passes) ?: continue
            when (val child = canForce(next.first, StoneColor.White, next.second, depth - 1)) {
                is Force.TimedOut -> sawTimeout = true
                is Force.Yes -> return Force.Yes(child.proofPly + 1, child.nodes)
                is Force.No -> totalNodes += child.nodes
                Force.Unknown -> {
                    anyUnknown = true
                    allNo = false
                }
            }
        }
        return when {
            sawTimeout -> Force.TimedOut
            anyUnknown -> Force.Unknown
            allNo -> Force.No(totalNodes)
            else -> Force.Unknown
        }
    }

    private fun andMoves(
        position: Position,
        passes: Int,
        depth: Int,
        moves: List<Action>,
    ): Force {
        var anyUnknown = false
        var sawTimeout = false
        var worstPly = 0
        var totalNodes = 0
        var allYes = true
        for (action in moves) {
            val next = applyAction(position, action, StoneColor.White, passes) ?: continue
            when (val child = canForce(next.first, StoneColor.Black, next.second, depth - 1)) {
                is Force.TimedOut -> {
                    sawTimeout = true
                    allYes = false
                }
                is Force.No -> return Force.No(child.nodes)
                is Force.Yes -> {
                    totalNodes += child.nodes
                    if (child.proofPly + 1 > worstPly) worstPly = child.proofPly + 1
                }
                Force.Unknown -> {
                    anyUnknown = true
                    allYes = false
                }
            }
        }
        return when {
            sawTimeout -> Force.TimedOut
            anyUnknown -> Force.Unknown
            allYes -> Force.Yes(worstPly, totalNodes)
            else -> Force.Unknown
        }
    }

    private fun cachedOutcome(position: Position, bothPassed: Boolean): Outcome {
        val key = "${position.key}|$bothPassed"
        return outcomes.getOrPut(key) { classify(position, problem.targets, bothPassed) }
    }
}

internal fun actions(position: Position, toPlay: StoneColor, targets: Set<Point>): List<Action> {
    val region = relevantEmptyPoints(position, targets)
    val pool = if (region.isEmpty()) position.playable else region
    val libertyFirst = HashSet<Point>()
    val seen = HashSet<Point>()
    for (t in targets) {
        if (t !in position.stones || !seen.add(t)) continue
        val string = position.stringAt(t)
        seen.addAll(string)
        libertyFirst.addAll(position.liberties(string))
    }
    val legal = pool.filter { position.play(it, toPlay) != null }.ifEmpty { position.legalMoves(toPlay) }
    val ordered = legal.sortedWith(
        compareByDescending<Point> { if (isCapture(position, it, toPlay)) 2 else if (it in libertyFirst) 1 else 0 }
            .thenBy { it },
    )
    return ordered.map { Action.Move(it) } + Action.Pass
}

private fun isCapture(position: Position, point: Point, toPlay: StoneColor): Boolean {
    for (n in position.neighbors(point)) {
        if (position.stones[n] != toPlay.opposite) continue
        if (position.liberties(position.stringAt(n)).size == 1) return true
    }
    return false
}

internal fun relevantEmptyPoints(position: Position, targets: Set<Point>): Set<Point> {
    val relevant = LinkedHashSet<Point>()
    val seen = HashSet<Point>()
    val libertySet = LinkedHashSet<Point>()
    for (t in targets) {
        if (t !in position.stones || !seen.add(t)) continue
        val string = position.stringAt(t)
        seen.addAll(string)
        val libs = position.liberties(string)
        libertySet.addAll(libs)
        relevant.addAll(libs)
    }
    for (lib in libertySet) {
        for (n in position.neighbors(lib)) {
            if (n in position.stones) continue
            val adj = position.neighbors(n).count { it in libertySet }
            if (adj >= 2) relevant.add(n)
        }
    }
    return relevant
}

internal fun applyAction(
    position: Position,
    action: Action,
    toPlay: StoneColor,
    passes: Int,
): Pair<Position, Int>? = when (action) {
    Action.Pass -> position to (passes + 1)
    is Action.Move -> {
        val next = position.play(action.point, toPlay) ?: return null
        next to 0
    }
}

private fun actionRank(action: Action): Int = when (action) {
    Action.Pass -> Int.MAX_VALUE
    is Action.Move -> action.point.file * 20 + action.point.rank
}
