package com.neojou.tsumego.classify

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor

/**
 * ADR-0014 order: captured → Benson → 雙活 → 贏劫／輸劫 → 雙方已停則剩餘當死 → 未定.
 */
internal fun classify(position: Position, targets: Set<Point>, bothPassed: Boolean): Outcome {
    if (targets.isEmpty()) return Outcome.Unsettled
    val onBoard = targets.filter { it in position.stones }.toSet()
    if (onBoard.isEmpty()) return Outcome.UnconditionalDead

    val bensonBlack = bensonAlive(position, StoneColor.Black)
    val bensonWhite = bensonAlive(position, StoneColor.White)
    if (allTargetsAlive(onBoard, targets, position, bensonBlack, bensonWhite)) {
        return Outcome.UnconditionalLive
    }

    if (isSeki(position, targets, onBoard, bothPassed, bensonBlack, bensonWhite)) {
        return Outcome.Seki
    }

    val ko = classifyKo(position, targets, onBoard, bothPassed)
    if (ko != null) return ko

    if (bothPassed && allTargetsDeadAfterPass(onBoard, position, bensonBlack, bensonWhite)) {
        return Outcome.UnconditionalDead
    }
    return Outcome.Unsettled
}

private fun allTargetsAlive(
    onBoard: Set<Point>,
    targets: Set<Point>,
    position: Position,
    bensonBlack: Set<Point>,
    bensonWhite: Set<Point>,
): Boolean {
    if (onBoard.size != targets.size) return false
    return onBoard.all { point ->
        when (position.stones[point]) {
            StoneColor.Black -> point in bensonBlack
            StoneColor.White -> point in bensonWhite
            null -> false
        }
    }
}

private fun allTargetsDeadAfterPass(
    onBoard: Set<Point>,
    position: Position,
    bensonBlack: Set<Point>,
    bensonWhite: Set<Point>,
): Boolean {
    return onBoard.none { point ->
        when (position.stones[point]) {
            StoneColor.Black -> point in bensonBlack
            StoneColor.White -> point in bensonWhite
            null -> false
        }
    }
}

private fun isSeki(
    position: Position,
    targets: Set<Point>,
    onBoard: Set<Point>,
    bothPassed: Boolean,
    bensonBlack: Set<Point>,
    bensonWhite: Set<Point>,
): Boolean {
    if (!bothPassed) return false
    val blackTargets = onBoard.filter { position.stones[it] == StoneColor.Black }.toSet()
    val whiteTargets = onBoard.filter { position.stones[it] == StoneColor.White }.toSet()
    if (blackTargets.isEmpty() || whiteTargets.isEmpty()) return false
    if (blackTargets.any { it in bensonBlack }) return false
    if (whiteTargets.any { it in bensonWhite }) return false
    val blackLibs = libertiesOfTargets(position, blackTargets)
    val whiteLibs = libertiesOfTargets(position, whiteTargets)
    return blackLibs.intersect(whiteLibs).isNotEmpty()
}

private fun libertiesOfTargets(position: Position, targets: Set<Point>): Set<Point> {
    val libs = LinkedHashSet<Point>()
    val seen = HashSet<Point>()
    for (t in targets) {
        if (t in seen) continue
        val string = position.stringAt(t)
        seen.addAll(string)
        libs.addAll(position.liberties(string))
    }
    return libs
}

/**
 * Points of [color] that belong to a Benson-unconditionally-alive string.
 */
internal fun bensonAlive(position: Position, color: StoneColor): Set<Point> {
    var remaining = stringsOf(position, color)
    if (remaining.isEmpty()) return emptySet()
    while (true) {
        val remainingStones = remaining.flatten().toSet()
        val vital = HashMap<Set<Point>, Int>()
        for (string in remaining) vital[string] = 0
        val visited = HashSet<Point>()
        for (start in position.playable) {
            if (start in remainingStones || start in visited) continue
            val region = floodNon(position, start, remainingStones)
            visited.addAll(region)
            val empty = region.filter { it !in position.stones }
            if (empty.isEmpty()) continue
            val leaks = region.any { pt ->
                position.neighbors(pt).any { n ->
                    position.stones[n] == color && n !in remainingStones
                }
            }
            if (leaks) continue
            if (empty.any { e -> position.neighbors(e).none { it in remainingStones } }) continue
            for (string in remaining) {
                val adjacent = string.any { pt -> position.neighbors(pt).any { it in region } }
                if (adjacent) vital[string] = (vital[string] ?: 0) + 1
            }
        }
        val next = remaining.filter { (vital[it] ?: 0) >= 2 }.toSet()
        if (next == remaining) return remainingStones
        remaining = next
    }
}

private fun stringsOf(position: Position, color: StoneColor): Set<Set<Point>> {
    val seen = HashSet<Point>()
    val out = LinkedHashSet<Set<Point>>()
    for (p in position.playable.sorted()) {
        if (position.stones[p] != color || p in seen) continue
        val string = position.stringAt(p)
        seen.addAll(string)
        out.add(string)
    }
    return out
}

private fun floodNon(position: Position, start: Point, blocked: Set<Point>): Set<Point> {
    val seen = LinkedHashSet<Point>()
    val stack = ArrayDeque<Point>()
    stack.add(start)
    while (stack.isNotEmpty()) {
        val cur = stack.removeLast()
        if (!seen.add(cur)) continue
        for (n in position.neighbors(cur)) {
            if (n !in blocked && n !in seen) stack.add(n)
        }
    }
    return seen
}

private fun classifyKo(
    position: Position,
    targets: Set<Point>,
    onBoard: Set<Point>,
    bothPassed: Boolean,
): Outcome? {
    val blackKos = position.simpleKoCaptures(StoneColor.Black)
    val whiteKos = position.simpleKoCaptures(StoneColor.White)
    if (blackKos.isEmpty() && whiteKos.isEmpty()) return null

    val winBlack = resolveKos(position, StoneColor.Black)
    val winWhite = resolveKos(position, StoneColor.White)
    val blackWins = basicLife(winBlack, targets)
    val whiteWins = basicLife(winWhite, targets)

    val colors = onBoard.mapNotNull { position.stones[it] }.toSet()
    if (colors == setOf(StoneColor.Black)) {
        if (blackWins == BasicLife.Live && whiteWins != BasicLife.Live) return Outcome.KoLive
        if (blackWins == BasicLife.Live && whiteWins == BasicLife.Live) return Outcome.UnconditionalLive
    }
    if (colors == setOf(StoneColor.White)) {
        if (blackWins == BasicLife.Dead && whiteWins != BasicLife.Dead) return Outcome.KoKill
        if (blackWins == BasicLife.Dead && whiteWins == BasicLife.Dead && onBoard.isEmpty()) {
            return Outcome.UnconditionalDead
        }
    }
    if (bothPassed && blackWins == BasicLife.Seki && whiteWins == BasicLife.Seki) return Outcome.Seki
    return null
}

private enum class BasicLife { Live, Dead, Seki, Other }

private fun basicLife(position: Position, targets: Set<Point>): BasicLife {
    val onBoard = targets.filter { it in position.stones }.toSet()
    if (onBoard.isEmpty()) return BasicLife.Dead
    val bensonBlack = bensonAlive(position, StoneColor.Black)
    val bensonWhite = bensonAlive(position, StoneColor.White)
    if (allTargetsAlive(onBoard, targets, position, bensonBlack, bensonWhite)) return BasicLife.Live
    if (isSeki(position, targets, onBoard, bothPassed = true, bensonBlack, bensonWhite)) return BasicLife.Seki
    return BasicLife.Other
}

internal fun resolveKos(position: Position, winner: StoneColor): Position {
    var current = position
    repeat(8) {
        val takes = current.simpleKoCaptures(winner)
        if (takes.isNotEmpty()) {
            current = current.playIgnoringSuperko(takes.first(), winner) ?: return current
            return@repeat
        }
        val oppTakes = current.simpleKoCaptures(winner.opposite)
        if (oppTakes.isEmpty()) return current
        current = fillKoForWinner(current, oppTakes.first(), winner)
    }
    return current
}

/**
 * Winner already occupies the ko stone; treat the ko as won and filled:
 * remove the single stone and occupy the capture point.
 */
private fun fillKoForWinner(position: Position, oppTake: Point, winner: StoneColor): Position {
    val captured = position.neighbors(oppTake).filter { n ->
        position.stones[n] == winner && position.stringAt(n).size == 1 &&
            position.liberties(position.stringAt(n)) == setOf(oppTake)
    }
    if (captured.isEmpty()) return position
    val nextStones = position.stones.toMutableMap()
    captured.forEach { nextStones.remove(it) }
    nextStones[oppTake] = winner
    val next = Position(position.rect, position.edges, nextStones, position.past)
    return next.copy(past = next.past + next.key)
}
