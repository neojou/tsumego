package com.neojou.tsumego.solve

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.classify.firstOwnerMoveToTwoEyes
import com.neojou.tsumego.classify.minOwnerMovesToTwoEyes
import com.neojou.tsumego.classify.ownerCanForceLife
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
    val onPath: (String) -> Unit = {},
    val onPv: (white: Action, black: Action?, continuation: String, replace: Boolean) -> Unit = { _, _, _, _ -> },
    val onPathsComplete: () -> Unit = {},
    val hintWhite: Point? = null,
    val blackPlayedAway: Boolean = false,
)

fun interface Solver {
    suspend fun solve(input: SolverInput): SolverResult
}

private sealed class Force {
    data class Yes(
        val proofPly: Int,
        val nodes: Int,
        val zone: Set<Point> = emptySet(),
        val pv: List<Action> = emptyList(),
    ) : Force()
    data class No(
        val nodes: Int,
        val zone: Set<Point> = emptySet(),
        val pv: List<Action> = emptyList(),
    ) : Force()
    data object Unknown : Force()
    data object TimedOut : Force()
}

class AlphaBetaSolver(
    private val maxDepth: Int = 48,
) : Solver {
    override suspend fun solve(input: SolverInput): SolverResult {
        val liveAt = firstOwnerMoveToTwoEyes(input.position, input.problem.targets)
        if (liveAt != null && input.blackPlayedAway) {
            val next = input.position.play(liveAt, StoneColor.White)
            if (next != null && ownerCanForceLife(next, input.problem.targets)) {
                val action = Action.Move(liveAt)
                input.onPath(formatSearchPath(listOf(action), blackForces = false))
                input.onPv(action, null, branchWinner(false), false)
                input.onPathsComplete()
                return SolverResult.Refute(action)
            }
        }
        val search = Search(
            problem = input.problem,
            budget = input.budget,
            onPath = input.onPath,
            onPv = input.onPv,
            rootHintWhite = liveAt,
            rootKey = input.position.key,
        )
        var proven: Force? = null
        var provenDepth = 0
        for (depth in 1..maxDepth) {
            if (input.budget.expired()) return SolverResult.Timeout
            val result = search.canForce(
                position = input.position,
                toPlay = StoneColor.White,
                passes = input.consecutivePasses,
                depth = depth,
                path = emptyList(),
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
            is Force.Yes -> {
                input.onPathsComplete()
                search.pickResist(input, provenDepth)
            }
            is Force.No -> {
                input.onPathsComplete()
                search.pickRefute(input, provenDepth)
            }
        }
    }
}

private class Search(
    private val problem: Problem,
    private val budget: Budget,
    private val onPath: (String) -> Unit,
    private val onPv: (Action, Action?, String, Boolean) -> Unit,
    private val rootHintWhite: Point? = null,
    private val rootKey: String? = null,
) {
    private fun moves(position: Position, toPlay: StoneColor): List<Action> =
        actions(
            position,
            toPlay,
            problem,
            hintWhite = rootHintWhite.takeIf { toPlay == StoneColor.White && position.key == rootKey },
        )

    private val proven = HashMap<String, Force>()
    private val outcomes = HashMap<String, Outcome>()
    private val nodes = IntArray(1)

    suspend fun pickResist(input: SolverInput, proveDepth: Int): SolverResult {
        val liveAt = firstOwnerMoveToTwoEyes(input.position, problem.targets)
        val scored = ArrayList<ResistScore>()
        for (action in moves(input.position, StoneColor.White)) {
            if (budget.expired()) return SolverResult.Timeout
            val next = applyAction(input.position, action, StoneColor.White, input.consecutivePasses) ?: continue
            var found: Force.Yes? = null
            for (depth in 1..proveDepth) {
                val child = canForce(next.first, StoneColor.Black, next.second, depth, listOf(action), record = true)
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
            if (found != null) {
                val winning = countWinningBlackReplies(next.first, next.second, proveDepth, action)
                    ?: return SolverResult.Timeout
                scored += ResistScore(
                    action = action,
                    winningBlack = winning,
                    proofPly = found.proofPly,
                    nodes = found.nodes,
                    livePoint = action is Action.Move && action.point == liveAt,
                )
            }
        }
        if (scored.isEmpty()) return SolverResult.Resist(Action.Pass)
        val best = scored.minWith(resistOrder)
        return SolverResult.Resist(best.action)
    }

    private suspend fun countWinningBlackReplies(
        position: Position,
        passes: Int,
        proveDepth: Int,
        white: Action,
    ): Int? {
        var win = 0
        val rankDepth = minOf(proveDepth - 1, RANK_WIN_PLY).coerceAtLeast(0)
        for (black in moves(position, StoneColor.Black)) {
            if (budget.expired()) return null
            val afterBlack = applyAction(position, black, StoneColor.Black, passes) ?: continue
            val key = ttKey(afterBlack.first, StoneColor.White, afterBlack.second)
            val child = when (val hit = proven[key]) {
                is Force.Yes, is Force.No -> hit
                else -> canForce(
                    afterBlack.first,
                    StoneColor.White,
                    afterBlack.second,
                    rankDepth,
                    listOf(white, black),
                    record = false,
                )
            }
            when (child) {
                is Force.Yes -> win++
                is Force.TimedOut -> return null
                else -> Unit
            }
        }
        return win
    }

    suspend fun pickRefute(input: SolverInput, proveDepth: Int): SolverResult {
        val preventing = ArrayList<Action>()
        for (action in moves(input.position, StoneColor.White)) {
            if (budget.expired()) return SolverResult.Timeout
            val next = applyAction(input.position, action, StoneColor.White, input.consecutivePasses) ?: continue
            var child: Force = Force.Unknown
            for (depth in 1..proveDepth) {
                child = canForce(next.first, StoneColor.Black, next.second, depth, listOf(action))
                if (child is Force.TimedOut) return SolverResult.Timeout
                if (child is Force.Yes || child is Force.No) break
            }
            if (child is Force.No) preventing += action
        }
        if (preventing.isEmpty()) return SolverResult.Refute(Action.Pass)
        val liveAt = firstOwnerMoveToTwoEyes(input.position, problem.targets)
        if (liveAt != null) {
            val liveMove = Action.Move(liveAt)
            if (liveMove in preventing) return SolverResult.Refute(liveMove)
        }
        val best = preventing.minWith(
            compareBy<Action> { refuteLifeTier(it, input.position) }
                .thenBy { minOwnerMovesToTwoEyes(afterWhite(input.position, it), problem.targets) ?: 99 }
                .thenBy { if (it is Action.Pass) 1 else 0 }
                .thenBy { actionRank(it) },
        )
        return SolverResult.Refute(best)
    }

    private fun afterWhite(position: Position, action: Action): Position = when (action) {
        Action.Pass -> position
        is Action.Move -> position.play(action.point, StoneColor.White) ?: position
    }

    private fun refuteLifeTier(action: Action, from: Position): Int {
        val position = afterWhite(from, action)
        val outcome = cachedOutcome(position, bothPassed = false)
        return when {
            outcome == Outcome.UnconditionalLive -> 0
            outcome != Outcome.Unsettled && !problem.goal.isSuccess(outcome) -> 1
            ownerCanForceLife(position, problem.targets) -> 2
            else -> 3
        }
    }

    suspend fun canForce(
        position: Position,
        toPlay: StoneColor,
        passes: Int,
        depth: Int,
        path: List<Action>,
        record: Boolean = true,
    ): Force {
        nodes[0]++
        if ((nodes[0] and 7) == 0) {
            kotlinx.coroutines.yield()
            if (budget.expired()) return Force.TimedOut
        }

        val bothPassed = passes >= 2
        val outcome = cachedOutcome(position, bothPassed)
        val terminal = when {
            problem.goal.isSuccess(outcome) ->
                Force.Yes(proofPly = 0, nodes = 1, zone = terminalRelevanceZone(position, problem, outcome, bothPassed))
            outcome != Outcome.Unsettled || bothPassed ->
                Force.No(nodes = 1, zone = terminalRelevanceZone(position, problem, outcome, bothPassed))
            else -> null
        }
        if (terminal != null) {
            if (record && path.isNotEmpty() && path.none { it is Action.Pass }) {
                onPath(formatSearchPath(path, blackForces = terminal is Force.Yes))
                seedPv(path, blackForces = terminal is Force.Yes)
            }
            proven[ttKey(position, toPlay, passes)] = stripZone(terminal)
            return terminal
        }

        val key = ttKey(position, toPlay, passes)
        proven[key]?.let { return it }
        if (depth <= 0) return Force.Unknown

        val listed = moves(position, toPlay)
        val result = if (toPlay == StoneColor.Black) {
            orMoves(position, passes, depth, listed, path, record)
        } else {
            andMoves(position, passes, depth, listed, path, record)
        }
        if (result is Force.Yes || result is Force.No) proven[key] = stripZone(result)
        return result
    }

    private fun seedPv(path: List<Action>, blackForces: Boolean) {
        emitPv(path[0], path.getOrNull(1), formatSearchPath(path.drop(2), blackForces), false)
    }

    private fun emitPv(white: Action, black: Action?, continuation: String, replace: Boolean) {
        if ("停" in continuation) return
        onPv(white, black, continuation, replace)
    }

    private suspend fun orMoves(
        position: Position,
        passes: Int,
        depth: Int,
        moves: List<Action>,
        path: List<Action>,
        record: Boolean,
    ): Force {
        var anyUnknown = false
        var sawTimeout = false
        var totalNodes = 0
        var allNo = true
        var bestYes: Force.Yes? = null
        val noZones = ArrayList<Set<Point>>()
        val pending = ArrayDeque(moves)
        val searched = HashSet<Action>()
        while (pending.isNotEmpty()) {
            val action = pending.removeFirst()
            if (!searched.add(action)) continue
            val next = applyAction(position, action, StoneColor.Black, passes) ?: continue
            when (val child = canForce(next.first, StoneColor.White, next.second, depth - 1, path + action, record)) {
                is Force.TimedOut -> sawTimeout = true
                is Force.Yes -> {
                    allNo = false
                    val yes = Force.Yes(
                        child.proofPly + 1,
                        child.nodes,
                        dilate(child.zone, position, action),
                        pv = listOf(action) + child.pv,
                    )
                    val current = bestYes
                    val better = current == null ||
                        yes.proofPly < current.proofPly ||
                        (yes.proofPly == current.proofPly && yes.nodes < current.nodes)
                    if (better) bestYes = yes
                    if (yes.proofPly <= 1) return yes
                }
                is Force.No -> {
                    totalNodes += child.nodes
                    noZones += child.zone
                    if (isNullMove(action, child.zone)) {
                        pending.clear()
                        pending.addAll(retainMustPlay(moves, child.zone, searched))
                    }
                }
                Force.Unknown -> {
                    anyUnknown = true
                    allNo = false
                }
            }
        }
        val provenYes = bestYes
        return when {
            provenYes != null -> provenYes
            sawTimeout -> Force.TimedOut
            anyUnknown -> Force.Unknown
            allNo -> Force.No(totalNodes, dilate(noZones.flatten().toSet(), position))
            else -> Force.Unknown
        }
    }

    private suspend fun andMoves(
        position: Position,
        passes: Int,
        depth: Int,
        moves: List<Action>,
        path: List<Action>,
        record: Boolean,
    ): Force {
        var anyUnknown = false
        var sawTimeout = false
        var worstPly = 0
        var totalNodes = 0
        var allYes = true
        val yesZones = ArrayList<Set<Point>>()
        val yesKids = ArrayList<Pair<Action, Force.Yes>>()
        val pending = ArrayDeque(moves)
        val searched = HashSet<Action>()
        while (pending.isNotEmpty()) {
            val action = pending.removeFirst()
            if (!searched.add(action)) continue
            val next = applyAction(position, action, StoneColor.White, passes) ?: continue
            when (val child = canForce(next.first, StoneColor.Black, next.second, depth - 1, path + action, record)) {
                is Force.TimedOut -> {
                    sawTimeout = true
                    allYes = false
                }
                is Force.No -> {
                    val no = Force.No(
                        child.nodes,
                        dilate(child.zone, position, action),
                        pv = listOf(action) + child.pv,
                    )
                    if (record && path.size == 2) {
                        emitPv(path[0], path[1], formatSearchPath(no.pv, false), true)
                    }
                    return no
                }
                is Force.Yes -> {
                    totalNodes += child.nodes
                    if (child.proofPly + 1 > worstPly) worstPly = child.proofPly + 1
                    yesZones += child.zone
                    yesKids += action to child
                    if (isNullMove(action, child.zone)) {
                        pending.clear()
                        pending.addAll(retainMustPlay(moves, child.zone, searched))
                    }
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
            allYes -> {
                val pv = if (record && path.size == 2) {
                    resistPv(position, passes, depth, yesKids)
                } else {
                    val worst = yesKids.maxByOrNull { it.second.proofPly }
                    if (worst == null) emptyList() else listOf(worst.first) + worst.second.pv
                }
                if (record && path.size == 2) {
                    emitPv(path[0], path[1], formatSearchPath(pv, true), true)
                }
                Force.Yes(worstPly, totalNodes, dilate(yesZones.flatten().toSet(), position), pv)
            }
            else -> Force.Unknown
        }
    }

    private suspend fun resistPv(
        position: Position,
        passes: Int,
        depth: Int,
        yesKids: List<Pair<Action, Force.Yes>>,
    ): List<Action> {
        if (yesKids.isEmpty()) return emptyList()
        val liveAt = firstOwnerMoveToTwoEyes(position, problem.targets)
        val scored = ArrayList<Pair<ResistScore, List<Action>>>()
        for ((action, child) in yesKids) {
            val next = applyAction(position, action, StoneColor.White, passes) ?: continue
            val winning = countWinningBlackReplies(next.first, next.second, depth, action) ?: continue
            scored += ResistScore(
                action = action,
                winningBlack = winning,
                proofPly = child.proofPly,
                nodes = child.nodes,
                livePoint = action is Action.Move && action.point == liveAt,
            ) to (listOf(action) + child.pv)
        }
        if (scored.isEmpty()) {
            val (action, child) = yesKids.first()
            return listOf(action) + child.pv
        }
        return scored.minWith { a, b -> resistOrder.compare(a.first, b.first) }.second
    }

    private fun stripZone(result: Force): Force = when (result) {
        is Force.Yes -> Force.Yes(result.proofPly, result.nodes, pv = result.pv)
        is Force.No -> Force.No(result.nodes, pv = result.pv)
        else -> result
    }

    private fun ttKey(position: Position, toPlay: StoneColor, passes: Int): String =
        "${position.key}|${toPlay.name}|$passes|${position.past.sorted().joinToString()}"

    private fun cachedOutcome(position: Position, bothPassed: Boolean): Outcome {
        val key = "${position.key}|$bothPassed"
        return outcomes.getOrPut(key) { classify(position, problem.targets, bothPassed) }
    }
}

internal fun actions(
    position: Position,
    toPlay: StoneColor,
    problem: Problem,
    hintWhite: Point? = null,
    guess: Point? = null,
): List<Action> {
    val targets = problem.targets
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
    val saving = if (toPlay == StoneColor.White) immediateRefutations(position, problem, pool) else emptySet()
    val clamps = region - libertyFirst
    val extra = listOfNotNull(hintWhite, guess).filter { position.play(it, toPlay) != null }
    val legal = (pool + saving + extra).distinct().filter { position.play(it, toPlay) != null }
        .ifEmpty { position.legalMoves(toPlay) }
    val ordered = legal.sortedWith(
        compareByDescending<Point> {
            when {
                toPlay == StoneColor.White && it == hintWhite -> 6
                toPlay == StoneColor.White && it == guess -> 5
                it in saving -> 4
                isCapture(position, it, toPlay) -> 3
                it in clamps -> 2
                it in libertyFirst -> 1
                else -> 0
            }
        }.thenBy { it },
    )
    return ordered.map { Action.Move(it) } + Action.Pass
}

internal fun immediateRefutations(
    position: Position,
    problem: Problem,
    candidates: Collection<Point>,
): Set<Point> {
    val out = LinkedHashSet<Point>()
    for (point in candidates) {
        val next = position.play(point, StoneColor.White) ?: continue
        val outcome = classify(next, problem.targets, bothPassed = false)
        if (outcome != Outcome.Unsettled && !problem.goal.isSuccess(outcome)) out.add(point)
    }
    return out
}

internal fun isCapture(position: Position, point: Point, toPlay: StoneColor): Boolean {
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

/** Unproven black replies are not re-expanded at full proveDepth (7K T16: 20s/branch). */
private const val RANK_WIN_PLY = 3

private data class ResistScore(
    val action: Action,
    val winningBlack: Int,
    val proofPly: Int,
    val nodes: Int,
    val livePoint: Boolean,
)

/**
 * Narrower kill first (fewer proven winning black replies), then longer ply,
 * then more nodes, then 做活點, then smaller coordinates.
 */
private val resistOrder = compareBy<ResistScore> { it.winningBlack }
    .thenBy { if (it.action is Action.Pass) 1 else 0 }
    .thenByDescending { it.proofPly }
    .thenByDescending { it.nodes }
    .thenBy { if (it.livePoint) 0 else 1 }
    .thenBy { actionRank(it.action) }

private fun actionRank(action: Action): Int = when (action) {
    Action.Pass -> Int.MAX_VALUE
    is Action.Move -> action.point.file * 20 + action.point.rank
}

internal fun plyLabel(action: Action, blackToPlay: Boolean): String {
    val who = if (blackToPlay) "黑" else "白"
    return when (action) {
        Action.Pass -> "${who}停"
        is Action.Move -> "${who}下 ${action.point.label}"
    }
}

internal fun branchWinner(blackForces: Boolean): String = if (blackForces) "黑勝" else "白勝"

internal fun formatSearchPath(path: List<Action>, blackForces: Boolean): String {
    var blackToPlay = false
    val steps = path.map { action ->
        val label = plyLabel(action, blackToPlay)
        blackToPlay = !blackToPlay
        label
    }
    return (steps + branchWinner(blackForces)).joinToString(" -> ")
}
