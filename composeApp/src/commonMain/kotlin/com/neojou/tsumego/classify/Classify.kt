package com.neojou.tsumego.classify

import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor

/**
 * ADR-0014 order: captured → Benson → 雙活 → 贏劫／輸劫 → 雙方已停則剩餘當死 →
 * 目標色連續落子仍到不了 Benson → 未定.
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

    val ko = if (position.hasKoCandidate()) classifyKo(position, targets, onBoard, bothPassed) else null
    if (ko != null) return ko

    if (bothPassed && allTargetsDeadAfterPass(onBoard, position, bensonBlack, bensonWhite)) {
        return Outcome.UnconditionalDead
    }
    if (!ownerCanForceLife(position, targets)) {
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

private const val MAX_OWNER_FILL_SPACE = 8

/**
 * True if the unique target color can still reach Benson 無條件活
 * by playing only its own stones (attacker 脫先). If not, the group
 * cannot make two eyes even when ignored.
 */
internal fun isAwayFromTargets(position: Position, point: Point, targets: Set<Point>): Boolean {
    val onBoard = targets.filter { it in position.stones }.toSet()
    if (onBoard.isEmpty()) return true
    val (empty, group) = eyeSpaceRaw(position, onBoard)
    if (point in group || point in empty) return false
    return position.neighbors(point).none { it in group }
}

internal fun ownerCanForceLife(position: Position, targets: Set<Point>): Boolean {
    val onBoard = targets.filter { it in position.stones }.toSet()
    if (onBoard.isEmpty()) return false
    if (libertyFlood(position, onBoard).size > MAX_OWNER_FILL_SPACE) return true
    return minOwnerMovesToTwoEyes(position, targets) != null
}

/**
 * The first owner stone in a shortest fill to Benson. White-to-play 做活點.
 */
internal fun firstOwnerMoveToTwoEyes(position: Position, targets: Set<Point>): Point? {
    val onBoard = targets.filter { it in position.stones }.toSet()
    if (onBoard.isEmpty()) return null
    val colors = onBoard.mapNotNull { position.stones[it] }.toSet()
    if (colors.size != 1) return null
    val color = colors.single()
    val need = minOwnerMovesToTwoEyes(position, targets) ?: return null
    if (need <= 0) return null
    var best: Point? = null
    var bestEyes = -1
    for (point in eyeSpace(position, onBoard).sorted()) {
        val next = position.play(point, color) ?: continue
        val rest = minOwnerMovesToTwoEyes(next, targets) ?: continue
        if (rest != need - 1) continue
        val eyes = trueEyeCount(next, color)
        val current = best
        if (current == null || betterLivingMove(point, eyes, current, bestEyes, position.edges)) {
            best = point
            bestEyes = eyes
        }
    }
    return best
}

private fun betterLivingMove(
    candidate: Point,
    candidateEyes: Int,
    current: Point,
    currentEyes: Int,
    edges: Edges,
): Boolean {
    if (candidateEyes != currentEyes) return candidateEyes > currentEyes
    val c = cornerPull(candidate, edges)
    val k = cornerPull(current, edges)
    if (c.first != k.first) return c.first > k.first
    if (c.second != k.second) return c.second > k.second
    return candidate < current
}

private fun cornerPull(point: Point, edges: Edges): Pair<Int, Int> {
    val filePull = when {
        edges.right == EdgeKind.Real && edges.left != EdgeKind.Real -> point.file
        edges.left == EdgeKind.Real && edges.right != EdgeKind.Real -> -point.file
        else -> 0
    }
    val rankPull = when {
        edges.top == EdgeKind.Real && edges.bottom != EdgeKind.Real -> point.rank
        edges.bottom == EdgeKind.Real && edges.top != EdgeKind.Real -> -point.rank
        else -> 0
    }
    return filePull to rankPull
}

internal fun trueEyeCount(position: Position, color: StoneColor): Int {
    var n = 0
    for (p in position.playable) {
        if (p in position.stones) continue
        if (position.neighbors(p).isNotEmpty() &&
            position.neighbors(p).all { position.stones[it] == color }
        ) {
            n++
        }
    }
    return n
}

/**
 * Fewest consecutive owner stones to Benson 無條件活, ignoring the attacker.
 * Null if the local eye space cannot make two eyes.
 */
internal fun minOwnerMovesToTwoEyes(position: Position, targets: Set<Point>): Int? {
    val onBoard = targets.filter { it in position.stones }.toSet()
    if (onBoard.isEmpty()) return null
    val colors = onBoard.mapNotNull { position.stones[it] }.toSet()
    if (colors.size != 1) return 0
    val color = colors.single()
    data class Node(val pos: Position, val depth: Int)
    val queue = ArrayDeque<Node>()
    val seen = HashSet<String>()
    queue.add(Node(position, 0))
    seen.add(position.key)
    var visited = 0
    while (queue.isNotEmpty() && visited < 4000) {
        visited++
        val (pos, depth) = queue.removeFirst()
        val remain = targets.filter { it in pos.stones }.toSet()
        if (remain.isEmpty()) continue
        val alive = bensonAlive(pos, color)
        if (remain.all { it in alive }) return depth
        val candidates = eyeSpace(pos, remain)
        if (depth >= MAX_OWNER_FILL_SPACE) continue
        for (point in candidates.sorted()) {
            val next = pos.play(point, color) ?: continue
            if (seen.add(next.key)) queue.add(Node(next, depth + 1))
        }
    }
    return null
}

/**
 * Empty points next to the target strings, plus one more step of empty.
 * Full-board floods from a liberty treat outside dame as eye space and
 * the size cap then pretends the group can live.
 */
private fun libertyFlood(position: Position, onBoard: Set<Point>): Set<Point> {
    val start = LinkedHashSet<Point>()
    val seenStr = HashSet<Point>()
    for (t in onBoard) {
        if (!seenStr.add(t)) continue
        val string = position.stringAt(t)
        seenStr.addAll(string)
        start.addAll(position.liberties(string))
    }
    val region = LinkedHashSet<Point>()
    val stack = ArrayDeque(start)
    while (stack.isNotEmpty()) {
        val cur = stack.removeLast()
        if (cur in position.stones || !region.add(cur)) continue
        for (n in position.neighbors(cur)) {
            if (n !in position.stones && n !in region) stack.add(n)
        }
    }
    return region
}

private fun eyeSpaceRaw(position: Position, onBoard: Set<Point>): Pair<Set<Point>, Set<Point>> {
    val group = LinkedHashSet<Point>()
    val seenStr = HashSet<Point>()
    for (t in onBoard) {
        if (!seenStr.add(t)) continue
        val string = position.stringAt(t)
        seenStr.addAll(string)
        group.addAll(string)
    }
    val region = LinkedHashSet<Point>()
    for (p in group) {
        for (n in position.neighbors(p)) {
            if (n !in position.stones) region.add(n)
        }
    }
    for (lib in region.toList()) {
        for (n in position.neighbors(lib)) {
            if (n !in position.stones) region.add(n)
        }
    }
    return region to group
}

private fun eyeSpace(position: Position, onBoard: Set<Point>): Set<Point> {
    val (region, group) = eyeSpaceRaw(position, onBoard)
    if (region.size <= MAX_OWNER_FILL_SPACE) return region
    if (group.isEmpty()) return region
    return region.sortedWith(
        compareBy<Point> { p -> group.minOf { kotlin.math.abs(it.file - p.file) + kotlin.math.abs(it.rank - p.rank) } }
            .thenBy { it },
    ).take(MAX_OWNER_FILL_SPACE).toSet()
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
    val blackTargets = onBoard.filter { position.stones[it] == StoneColor.Black }.toSet()
    val whiteTargets = onBoard.filter { position.stones[it] == StoneColor.White }.toSet()
    if (bothPassed &&
        blackTargets.isNotEmpty() &&
        whiteTargets.isNotEmpty() &&
        blackTargets.none { it in bensonBlack } &&
        whiteTargets.none { it in bensonWhite }
    ) {
        val blackLibs = libertiesOfTargets(position, blackTargets)
        val whiteLibs = libertiesOfTargets(position, whiteTargets)
        if (blackLibs.intersect(whiteLibs).isNotEmpty()) return true
    }
    return deadlockSeki(position, whiteTargets, bensonBlack, bensonWhite)
}

/**
 * 殺棋目標只有白時：每一串都與鄰近黑共氣、安全詰氣提不掉，且詰完仍能做活。
 * 兩塊白棋的氣加總成 4 點共氣不是雙活（7K one-more：T18 提 T17 後 S18–S19 仍可被 R19 詰氣）。
 * 只看「填了提不到」也會把 small_trick 連回後的詰氣當成雙活。
 */
private fun deadlockSeki(
    position: Position,
    whiteTargets: Set<Point>,
    bensonBlack: Set<Point>,
    bensonWhite: Set<Point>,
): Boolean {
    if (whiteTargets.isEmpty()) return false
    if (whiteTargets.any { it in bensonWhite }) return false
    if (position.simpleKoCaptures(StoneColor.Black).isNotEmpty()) return false
    if (position.simpleKoCaptures(StoneColor.White).isNotEmpty()) return false
    if (!ownerCanForceLife(position, whiteTargets)) return false
    val strings = whiteStrings(position, whiteTargets)
    if (strings.isEmpty()) return false
    if (strings.any { attackerCanCapture(position, it, 0) }) return false
    val whiteLibs = libertiesOfTargets(position, whiteTargets)
    val adjBlack = whiteLibs.flatMap { position.neighbors(it) }
        .filter { position.stones[it] == StoneColor.Black }.toSet()
    if (adjBlack.isEmpty()) return false
    if (adjBlack.any { it in bensonBlack }) return false
    val blackLibs = libertiesOfTargets(position, adjBlack)
    val shared = whiteLibs.intersect(blackLibs)
    if (whiteLibs.size != shared.size || shared.size < 2 || shared.size > 4) return false
    if (shared.any { fillCapturesEither(position, it, whiteTargets, adjBlack) }) return false
    for (point in shared) {
        val next = position.play(point, StoneColor.Black) ?: continue
        if (next.liberties(next.stringAt(point)).size < 2) continue
        if (!ownerCanForceLife(next, whiteTargets)) return false
    }
    return true
}

private fun whiteStrings(position: Position, whiteTargets: Set<Point>): List<Set<Point>> {
    val seen = HashSet<Point>()
    val out = ArrayList<Set<Point>>()
    for (t in whiteTargets) {
        if (t !in position.stones || !seen.add(t)) continue
        val string = position.stringAt(t)
        seen.addAll(string)
        out += string
    }
    return out
}

/** 黑可安全詰氣直到提掉這串 → 不是雙活。入氣的填不算安全。 */
private fun attackerCanCapture(position: Position, stones: Set<Point>, depth: Int): Boolean {
    if (depth > 6) return false
    val remaining = stones.filter { it in position.stones }.toSet()
    if (remaining.isEmpty()) return true
    val libs = libertiesOfTargets(position, remaining)
    for (point in libs) {
        val next = position.play(point, StoneColor.Black) ?: continue
        if (remaining.any { it !in next.stones }) return true
        if (next.liberties(next.stringAt(point)).size < 2) continue
        if (attackerCanCapture(next, remaining, depth + 1)) return true
    }
    return false
}

private fun fillCapturesEither(
    position: Position,
    point: Point,
    whiteTargets: Set<Point>,
    adjBlack: Set<Point>,
): Boolean {
    val asBlack = position.play(point, StoneColor.Black)
    if (asBlack != null && whiteTargets.any { it in position.stones && it !in asBlack.stones }) return true
    val asWhite = position.play(point, StoneColor.White)
    if (asWhite != null && adjBlack.any { it !in asWhite.stones }) return true
    return false
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
            val empties = empty.toSet()
            for (string in remaining) {
                val adjacent = string.any { pt -> position.neighbors(pt).any { it in empties } }
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
        if (blackWins == BasicLife.Live && whiteWins == BasicLife.Live) return Outcome.UnconditionalLive
        if (onBoard.isEmpty()) return Outcome.UnconditionalDead
        val koPoints = blackKos + whiteKos
        if (!canCaptureTargetBesidesKo(position, targets, koPoints)) return Outcome.KoKill
        if (!ownerCanForceLife(position, targets)) return Outcome.KoKill
        return null
    }
    if (bothPassed && blackWins == BasicLife.Seki && whiteWins == BasicLife.Seki) return Outcome.Seki
    return null
}

private fun canCaptureTargetBesidesKo(
    position: Position,
    targets: Set<Point>,
    koPoints: Collection<Point>,
): Boolean {
    for (color in arrayOf(StoneColor.Black, StoneColor.White)) {
        for (point in position.playable) {
            if (point in koPoints) continue
            val next = position.play(point, color) ?: continue
            if (targets.any { it in position.stones && it !in next.stones }) return true
        }
    }
    return false
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
