package com.neojou.tsumego.solve

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.classify.classify
import kotlin.random.Random

/**
 * If black passes at the start, the point where white can 做活 (or otherwise
 * immediately end black's chance). Computed once; later searches try 白下
 * this point first.
 */
internal fun findOpeningWhiteLife(
    problem: Problem,
    random: Random = Random.Default,
    playouts: Int = 24,
): Point? {
    val position = Position.initial(problem)
    val candidates = whiteCandidatePoints(position, problem)
    if (candidates.isEmpty()) return null
    val immediate = candidates.filter { whiteMoveEndsBlackChance(position, problem, it) }
    if (immediate.isNotEmpty()) return immediate.minOrNull()
    return rankMovesByPlayout(
        position = position,
        toPlay = StoneColor.White,
        problem = problem,
        candidates = candidates,
        random = random,
        playouts = playouts,
    ).firstOrNull()
}

internal fun whiteCandidatePoints(position: Position, problem: Problem): List<Point> {
    val region = relevantEmptyPoints(position, problem.targets)
    val pool = if (region.isEmpty()) position.playable else region
    return pool.filter { position.play(it, StoneColor.White) != null }.sorted()
}

internal fun whiteMoveEndsBlackChance(position: Position, problem: Problem, point: Point): Boolean {
    val next = position.play(point, StoneColor.White) ?: return false
    val outcome = classify(next, problem.targets, bothPassed = false)
    return outcome != Outcome.Unsettled && !problem.goal.isSuccess(outcome)
}

/**
 * Guess-then-verify ordering: rank [candidates] by how often black fails
 * after that white (or succeeds after that black) move, using random playouts.
 * Immediate terminal 做活 scores above any playout rate so the vital point wins.
 */
internal fun rankMovesByPlayout(
    position: Position,
    toPlay: StoneColor,
    problem: Problem,
    candidates: List<Point>,
    random: Random,
    playouts: Int,
): List<Point> {
    val legal = candidates.filter { position.play(it, toPlay) != null }
    if (legal.isEmpty()) return emptyList()
    val scored = legal.map { point ->
        val next = position.play(point, toPlay) ?: return@map point to -2.0
        val outcome = classify(next, problem.targets, bothPassed = false)
        val blackFails = when {
            outcome != Outcome.Unsettled ->
                if (problem.goal.isSuccess(outcome)) 0.0 else 2.0
            else -> {
                var fails = 0
                repeat(playouts.coerceAtLeast(1)) {
                    if (playoutBlackFails(next, toPlay.opposite, 0, problem, random)) fails++
                }
                fails.toDouble() / playouts
            }
        }
        val score = if (toPlay == StoneColor.White) blackFails else (2.0 - blackFails)
        point to score
    }
    return scored.sortedWith(
        compareByDescending<Pair<Point, Double>> { it.second }.thenBy { it.first },
    ).map { it.first }
}

private fun playoutBlackFails(
    start: Position,
    toPlay: StoneColor,
    passes: Int,
    problem: Problem,
    random: Random,
    maxPly: Int = 24,
): Boolean {
    var position = start
    var side = toPlay
    var passCount = passes
    repeat(maxPly) {
        val both = passCount >= 2
        val outcome = classify(position, problem.targets, both)
        if (outcome != Outcome.Unsettled || both) {
            return !problem.goal.isSuccess(outcome)
        }
        val move = pickPlayoutMove(position, side, problem, random)
        if (move == null) {
            passCount++
        } else {
            val next = position.play(move, side)
            if (next == null) {
                passCount++
            } else {
                position = next
                passCount = 0
            }
        }
        side = side.opposite
    }
    val outcome = classify(position, problem.targets, bothPassed = true)
    return !problem.goal.isSuccess(outcome)
}

private fun pickPlayoutMove(
    position: Position,
    toPlay: StoneColor,
    problem: Problem,
    random: Random,
): Point? {
    val region = relevantEmptyPoints(position, problem.targets)
    val pool = if (region.isEmpty()) {
        position.legalMoves(toPlay)
    } else {
        region.mapNotNull { point -> point.takeIf { position.play(it, toPlay) != null } }
    }.ifEmpty { position.legalMoves(toPlay) }
    if (pool.isEmpty()) return null
    val captures = pool.filter { isCapture(position, it, toPlay) }
    val source = if (captures.isNotEmpty() && random.nextDouble() < 0.7) captures else pool
    return source[random.nextInt(source.size)]
}
