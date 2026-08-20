package com.neojou.tsumego.solve

import com.neojou.tsumego.board.Goal
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
        val rootToPlay = StoneColor.White
        var proven: Force? = null
        for (depth in 1..maxDepth) {
            if (input.budget.expired()) return SolverResult.Timeout
            val nodes = IntArray(1)
            val result = canForce(
                position = input.position,
                toPlay = rootToPlay,
                passes = input.consecutivePasses,
                goal = input.problem.goal,
                targets = input.problem.targets,
                depth = depth,
                budget = input.budget,
                nodes = nodes,
            )
            when (result) {
                is Force.TimedOut -> return SolverResult.Timeout
                is Force.Yes, is Force.No -> {
                    proven = result
                    break
                }
                Force.Unknown -> Unit
            }
        }
        return when (val p = proven) {
            null, Force.Unknown -> SolverResult.Timeout
            is Force.TimedOut -> SolverResult.Timeout
            is Force.Yes -> pickResist(input)
            is Force.No -> pickRefute(input)
        }
    }

    private fun pickResist(input: SolverInput): SolverResult {
        val scored = ArrayList<Triple<Action, Int, Int>>()
        for (action in actions(input.position, StoneColor.White)) {
            if (input.budget.expired()) return SolverResult.Timeout
            val next = applyAction(input.position, action, StoneColor.White, input.consecutivePasses) ?: continue
            val nodes = IntArray(1)
            val child = canForce(
                next.first,
                StoneColor.Black,
                next.second,
                input.problem.goal,
                input.problem.targets,
                depth = maxDepth,
                budget = input.budget,
                nodes = nodes,
            )
            if (child is Force.TimedOut) return SolverResult.Timeout
            if (child is Force.Yes) scored += Triple(action, child.proofPly, child.nodes)
        }
        if (scored.isEmpty()) {
            return SolverResult.Resist(Action.Pass)
        }
        val best = scored.maxWith(
            compareBy<Triple<Action, Int, Int>> { it.second }
                .thenBy { if (it.first is Action.Pass) 0 else 1 }
                .thenBy { it.third }
                .thenBy { -actionRank(it.first) },
        )
        return SolverResult.Resist(best.first)
    }

    private fun pickRefute(input: SolverInput): SolverResult {
        val preventing = ArrayList<Action>()
        for (action in actions(input.position, StoneColor.White)) {
            if (input.budget.expired()) return SolverResult.Timeout
            val next = applyAction(input.position, action, StoneColor.White, input.consecutivePasses) ?: continue
            val nodes = IntArray(1)
            val child = canForce(
                next.first,
                StoneColor.Black,
                next.second,
                input.problem.goal,
                input.problem.targets,
                depth = maxDepth,
                budget = input.budget,
                nodes = nodes,
            )
            if (child is Force.TimedOut) return SolverResult.Timeout
            if (child is Force.No) preventing += action
        }
        if (preventing.isEmpty()) return SolverResult.Refute(Action.Pass)
        val stones = preventing.filterIsInstance<Action.Move>().sortedBy { it.point }
        return SolverResult.Refute(stones.firstOrNull() ?: Action.Pass)
    }

    private fun canForce(
        position: Position,
        toPlay: StoneColor,
        passes: Int,
        goal: Goal,
        targets: Set<Point>,
        depth: Int,
        budget: Budget,
        nodes: IntArray,
    ): Force {
        nodes[0]++
        if ((nodes[0] and 31) == 0 && budget.expired()) return Force.TimedOut

        val bothPassed = passes >= 2
        val outcome = classify(position, targets, bothPassed)
        if (goal.isSuccess(outcome)) return Force.Yes(proofPly = 0, nodes = 1)
        if (outcome != Outcome.Unsettled) return Force.No(nodes = 1)
        if (bothPassed) return Force.No(nodes = 1)
        if (depth <= 0) return Force.Unknown

        val moves = actions(position, toPlay)
        if (toPlay == StoneColor.Black) {
            var anyUnknown = false
            var sawTimeout = false
            var bestPly = Int.MAX_VALUE
            var totalNodes = 0
            var anyYes = false
            var allNo = true
            for (action in moves) {
                val next = applyAction(position, action, toPlay, passes) ?: continue
                val child = canForce(next.first, StoneColor.White, next.second, goal, targets, depth - 1, budget, nodes)
                when (child) {
                    is Force.TimedOut -> sawTimeout = true
                    is Force.Yes -> {
                        anyYes = true
                        allNo = false
                        totalNodes += child.nodes
                        if (child.proofPly + 1 < bestPly) bestPly = child.proofPly + 1
                    }
                    is Force.No -> totalNodes += child.nodes
                    Force.Unknown -> {
                        anyUnknown = true
                        allNo = false
                    }
                }
            }
            return when {
                anyYes -> Force.Yes(bestPly, totalNodes)
                sawTimeout -> Force.TimedOut
                anyUnknown -> Force.Unknown
                allNo -> Force.No(totalNodes)
                else -> Force.Unknown
            }
        } else {
            var anyUnknown = false
            var sawTimeout = false
            var worstPly = 0
            var totalNodes = 0
            var allYes = true
            var anyNo = false
            for (action in moves) {
                val next = applyAction(position, action, toPlay, passes) ?: continue
                val child = canForce(next.first, StoneColor.Black, next.second, goal, targets, depth - 1, budget, nodes)
                when (child) {
                    is Force.TimedOut -> {
                        sawTimeout = true
                        allYes = false
                    }
                    is Force.No -> {
                        anyNo = true
                        allYes = false
                        totalNodes += child.nodes
                    }
                    is Force.Yes -> {
                        totalNodes += child.nodes
                        if (child.proofPly + 1 > worstPly) worstPly = child.proofPly + 1
                    }
                    Force.Unknown -> {
                        anyUnknown = true
                        allYes = false
                    }
                }
                if (anyNo) break
            }
            return when {
                anyNo -> Force.No(totalNodes)
                sawTimeout -> Force.TimedOut
                anyUnknown -> Force.Unknown
                allYes -> Force.Yes(worstPly, totalNodes)
                else -> Force.Unknown
            }
        }
    }
}

internal fun actions(position: Position, toPlay: StoneColor): List<Action> {
    val moves = position.legalMoves(toPlay)
    return moves.map { Action.Move(it) } + Action.Pass
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
