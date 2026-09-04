package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.classify.bensonAlive
import kotlin.time.TimeSource

internal const val LDRZ_DEFAULT_NODE_CAP = 200_000
internal const val LDRZ_DEFAULT_TIME_MS = 300_000L
private const val MAX_PLY = 48


internal data class LdrzSearchVal(
    val status: LdrzStatus,
    val line: List<Point>,
    val zone: Set<Point> = emptySet(),
)

/**
 * Relevance-Zone Based Search for life/death (Shih et al.).
 * OR = defender (TOLIVE / UCA). AND = attacker (TOKILL / capture all crucial).
 * Does not read [LdrzProblem.answerFirstMove]. Does not call Solver.kt.
 */
internal class LdrzSearch(
    private val problem: LdrzProblem,
    private val nodeCap: Int,
    private val timeLimitMs: Long,
    private val onProgress: (LdrzProgress) -> Unit = {},
) {
    private val defender: StoneColor = problem.defenderColor()
    private val attacker: StoneColor = defender.opposite
    private val crucial: Set<Point> = problem.defenderTargets()
    private val whitelist: Set<Point> = LdrzZone.playableRegion(problem)
    private val started = TimeSource.Monotonic.markNow()
    private val proven = HashMap<String, Pair<Int, LdrzSearchVal>>()
    var nodes: Int = 0
        private set
    var currentZone: Set<Point> = emptySet()
        private set

    fun run(root: Position, toPlay: StoneColor): LdrzSearchVal {
        if (crucial.isEmpty()) {
            return LdrzSearchVal(LdrzStatus.ERROR, emptyList())
        }
        currentZone = LdrzZone.seed(problem, root)
        emit("決定相關區（${currentZone.size} 點）")
        nodes++
        val leaf = evaluate(root)
        if (leaf != null) {
            currentZone = leaf.zone
            emit("相關區已定（${currentZone.size} 點）")
            return leaf
        }
        emit("探測做活（攻擊方脫先）")
        val found = forceLive(root, toPlay, ply = 0)
        currentZone = found.zone.ifEmpty { currentZone }
        emit(
            if (found.status == LdrzStatus.UNSETTLED) {
                "相關區（${currentZone.size} 點）　未證明"
            } else {
                "相關區已定（${currentZone.size} 點）　${found.status.name}"
            },
        )
        return found.copy(zone = currentZone)
    }

    /**
     * RZS: live-vs-pass gives the zone; attacker moves outside it are 空手.
     * Defender only needs a move that still lives against every in-zone attack.
     */
    private fun forceLive(pos: Position, toPlay: StoneColor, ply: Int): LdrzSearchVal {
        bump()
        if (expired() || ply > MAX_PLY) {
            return LdrzSearchVal(LdrzStatus.UNSETTLED, emptyList(), currentZone)
        }
        evaluate(pos)?.let { return it }
        val key = ttKey(pos, toPlay, ply.coerceAtMost(2))
        proven[key]?.let { (_, value) ->
            if (value.status == LdrzStatus.ALIVE || value.status == LdrzStatus.DEAD) return value
        }
        val result = if (toPlay == defender) forceOr(pos, ply) else forceAnd(pos, ply)
        if (result.status == LdrzStatus.ALIVE || result.status == LdrzStatus.DEAD) {
            proven[key] = Int.MAX_VALUE to result
            if (result.zone.isNotEmpty()) currentZone = result.zone
        }
        return result
    }

    private fun forceOr(pos: Position, ply: Int): LdrzSearchVal {
        val hint = fillUntilUca(pos, maxPly = 16)
        if (hint != null && hint.zone.isNotEmpty()) {
            currentZone = LdrzZone.dilate(hint.zone, pos)
            emit("相關區已定（${currentZone.size} 點），驗證攻擊方")
        }
        val hintFirst = hint?.line?.firstOrNull()
        val moves = orderedMoves(pos, defender)
        val ordered = if (hintFirst == null) {
            moves
        } else {
            val hintMove = LdrzAction.Move(hintFirst)
            listOf(hintMove) + moves.filter { it != hintMove }
        }
        var anyUnknown = false
        val deadZones = ArrayList<Set<Point>>()
        var bestLive: LdrzSearchVal? = null
        var bestScore = Int.MIN_VALUE
        for (action in ordered) {
            if (expired()) {
                anyUnknown = true
                break
            }
            if (action is LdrzAction.Pass) continue
            val point = (action as LdrzAction.Move).point
            val next = pos.play(point, defender) ?: continue
            val child = forceLive(next, attacker, ply + 1)
            when (child.status) {
                LdrzStatus.ALIVE -> {
                    val scored = LdrzSearchVal(
                        LdrzStatus.ALIVE,
                        listOf(point) + child.line,
                        LdrzZone.dilate(child.zone.ifEmpty { hint?.zone ?: currentZone }, pos, point),
                    )
                    val score = eyeVitalScore(pos, point)
                    if (score > bestScore) {
                        bestScore = score
                        bestLive = scored
                    }
                }
                LdrzStatus.DEAD -> deadZones += child.zone
                LdrzStatus.UNSETTLED, LdrzStatus.ERROR -> anyUnknown = true
            }
        }
        bestLive?.let { return it }
        return LdrzSearchVal(LdrzStatus.UNSETTLED, emptyList(), currentZone)
    }

    private fun forceAnd(pos: Position, ply: Int): LdrzSearchVal {
        val vsPass = fillUntilUca(pos, maxPly = 16)
        if (vsPass != null) {
            currentZone = LdrzZone.dilate(vsPass.zone, pos)
            emit("相關區已定（${currentZone.size} 點），驗證攻擊方")
        }
        val zone = currentZone
        var anyUnknown = false
        val liveZones = ArrayList<Set<Point>>()
        if (vsPass != null) liveZones += vsPass.zone
        for (action in orderedMoves(pos, attacker)) {
            if (expired()) {
                anyUnknown = true
                break
            }
            if (action is LdrzAction.Pass) continue
            val point = (action as LdrzAction.Move).point
            if (vsPass != null && LdrzZone.isNullMove(point, zone)) continue
            val next = pos.play(point, attacker) ?: continue
            val ev = evaluate(next)
            if (ev?.status == LdrzStatus.DEAD) {
                return LdrzSearchVal(
                    LdrzStatus.DEAD,
                    listOf(point),
                    LdrzZone.dilate(ev.zone, pos, point),
                )
            }
            if (ev?.status == LdrzStatus.ALIVE) {
                liveZones += ev.zone
                continue
            }
            val stillLive = fillUntilUca(next, maxPly = 16)
            if (stillLive != null) {
                liveZones += stillLive.zone
                continue
            }
            val child = forceLive(next, defender, ply + 1)
            when (child.status) {
                LdrzStatus.DEAD -> {
                    return LdrzSearchVal(
                        LdrzStatus.DEAD,
                        listOf(point) + child.line,
                        LdrzZone.dilate(child.zone, pos, point),
                    )
                }
                LdrzStatus.ALIVE -> liveZones += child.zone
                LdrzStatus.UNSETTLED, LdrzStatus.ERROR -> anyUnknown = true
            }
        }
        if (vsPass != null) {
            return LdrzSearchVal(
                LdrzStatus.ALIVE,
                vsPass.line,
                LdrzZone.dilate(liveZones.flatten().toSet().ifEmpty { vsPass.zone }, pos),
            )
        }
        if (anyUnknown) return LdrzSearchVal(LdrzStatus.UNSETTLED, emptyList(), currentZone)
        if (liveZones.isNotEmpty()) {
            return LdrzSearchVal(
                LdrzStatus.ALIVE,
                emptyList(),
                LdrzZone.dilate(liveZones.flatten().toSet(), pos),
            )
        }
        return LdrzSearchVal(LdrzStatus.UNSETTLED, emptyList(), currentZone)
    }

    private fun evaluate(pos: Position): LdrzSearchVal? {
        val onBoard = crucial.filter { it in pos.stones }.toSet()
        if (onBoard.isEmpty()) {
            return LdrzSearchVal(LdrzStatus.DEAD, emptyList(), LdrzZone.terminalDead(pos, crucial))
        }
        val alive = bensonAlive(pos, defender)
        if (onBoard.any { it in alive }) {
            return LdrzSearchVal(LdrzStatus.ALIVE, emptyList(), LdrzZone.terminalAlive(pos, defender, crucial))
        }
        return null
    }

    private fun fillUntilUca(start: Position, maxPly: Int): LdrzSearchVal? {
        evaluate(start)?.let { ev ->
            if (ev.status == LdrzStatus.ALIVE) return ev
            if (ev.status == LdrzStatus.DEAD) return null
        }
        data class Node(val pos: Position, val line: List<Point>)
        val queue = ArrayDeque<Node>()
        val seen = HashSet<String>()
        queue.add(Node(start, emptyList()))
        seen.add(start.key)
        var visited = 0
        while (queue.isNotEmpty() && visited < 2000 && visited < maxPly * 80) {
            val (pos, line) = queue.removeFirst()
            visited++
            nodes++
            evaluate(pos)?.let { ev ->
                if (ev.status == LdrzStatus.ALIVE) return ev.copy(line = line)
            }
            if (line.size >= maxPly) continue
            for (p in fillCandidates(pos)) {
                val next = pos.play(p, defender) ?: continue
                if (!seen.add(next.key)) continue
                queue.add(Node(next, line + p))
            }
        }
        return null
    }

    private fun fillCandidates(pos: Position): List<Point> {
        val bound = currentZone.ifEmpty { LdrzZone.seed(problem, pos) }
        val focus = LdrzZone.searchPoints(pos, crucial, whitelist)
        val scope = focus.intersect(bound).ifEmpty { focus }
        return scope.filter { p ->
            cheapLegal(pos, p, defender) && !isTrueEyePoint(pos, p)
        }.sortedWith(
            compareByDescending<Point> { p ->
                var score = 0
                if (p.rank == 1 || p.rank == 19 || p.file == 0 || p.file == 18) score += 30
                score += pos.neighbors(p).count { pos.stones[it] == defender }
                score
            }.thenBy { it },
        )
    }

    /** Prefer 做眼急所 (真盤邊一線、對準棋串) over 連回 like T2. */
    private fun eyeVitalScore(pos: Position, point: Point): Int {
        val group = LinkedHashSet<Point>()
        for (t in crucial) {
            if (t in pos.stones) group.addAll(pos.stringAt(t))
        }
        if (group.isEmpty()) group.addAll(crucial)
        var score = 0
        if (point.rank == 1 || point.rank == 19 || point.file == 0 || point.file == 18) score += 100
        else if (point.rank == 2 || point.rank == 18 || point.file == 1 || point.file == 17) score += 20
        if (group.any { it.file == point.file && point.rank == 1 && it.rank > 1 }) score += 50
        if (group.any { it.file == point.file && point.rank == 19 && it.rank < 19 }) score += 50
        if (group.any { it.rank == point.rank && point.file == 18 && it.file < 18 }) score += 50
        if (group.any { it.rank == point.rank && point.file == 0 && it.file > 0 }) score += 50
        val midFile = group.map { it.file }.average()
        val midRank = group.map { it.rank }.average()
        score -= (kotlin.math.abs(point.file - midFile) + kotlin.math.abs(point.rank - midRank)).toInt() * 4
        val cornerish = group.minBy { minOf(it.file, 18 - it.file) + minOf(it.rank - 1, 19 - it.rank) }
        score -= (kotlin.math.abs(point.file - cornerish.file) + kotlin.math.abs(point.rank - cornerish.rank)) * 5
        score += pos.neighbors(point).count { pos.stones[it] == defender } * 3
        val nearOwn = pos.neighbors(point).count { n ->
            pos.stones[n] == defender && pos.stringAt(n).size == 1
        }
        score -= nearOwn * 15
        val libs = LinkedHashSet<Point>()
        for (p in group) libs.addAll(pos.liberties(pos.stringAt(p)))
        val onEdge = point.rank == 1 || point.rank == 19 || point.file == 0 || point.file == 18
        if (onEdge && point !in libs && libs.any { it in pos.neighbors(point) }) score += 200
        if (point.rank == 1) {
            val above = runCatching { Point(point.file, 2) }.getOrNull()
            val third = runCatching { Point(point.file, 3) }.getOrNull()
            if (above != null && third != null && above !in pos.stones && pos.stones[third] == defender) {
                score += 30 + point.file
            }
        }
        if (point.rank == 19) {
            val below = runCatching { Point(point.file, 18) }.getOrNull()
            val third = runCatching { Point(point.file, 17) }.getOrNull()
            if (below != null && third != null && below !in pos.stones && pos.stones[third] == defender) {
                score += 30 + (18 - point.file)
            }
        }
        return score
    }

    private fun isTrueEyePoint(pos: Position, point: Point): Boolean {
        val nbs = pos.neighbors(point)
        if (nbs.isEmpty()) return false
        if (nbs.any { pos.stones[it] != defender }) return false
        return true
    }

    private fun orderedMoves(pos: Position, toPlay: StoneColor): List<LdrzAction> {
        val bound = currentZone.ifEmpty { LdrzZone.seed(problem, pos) }
        val focus = LdrzZone.searchPoints(pos, crucial, whitelist)
        val scope = if (focus.isEmpty()) bound else focus.intersect(bound).ifEmpty { focus }
        val liberties = LinkedHashSet<Point>()
        val seen = HashSet<Point>()
        for (t in crucial) {
            if (t !in pos.stones || !seen.add(t)) continue
            val string = pos.stringAt(t)
            seen.addAll(string)
            liberties.addAll(pos.liberties(string))
        }
        val libList = ArrayList<Point>()
        val rest = ArrayList<Point>()
        for (p in scope) {
            if (p !in whitelist || p in pos.stones || !pos.rect.contains(p)) continue
            if (!cheapLegal(pos, p, toPlay)) continue
            if (p in liberties) libList += p else rest += p
        }
        libList.sort()
        rest.sortWith(
            compareByDescending<Point> { p -> pos.neighbors(p).count { pos.stones[it] == defender } }
                .thenBy { it },
        )
        val actions = ArrayList<LdrzAction>(libList.size + rest.size + 1)
        if (toPlay == attacker) actions += LdrzAction.Pass
        for (p in libList) actions += LdrzAction.Move(p)
        for (p in rest) actions += LdrzAction.Move(p)
        if (toPlay == defender) actions += LdrzAction.Pass
        return actions
    }

    private fun cheapLegal(pos: Position, point: Point, toPlay: StoneColor): Boolean {
        if (!pos.rect.contains(point) || point in pos.stones) return false
        var emptyNb = 0
        var captures = false
        for (n in pos.neighbors(point)) {
            when (pos.stones[n]) {
                null -> emptyNb++
                toPlay.opposite -> {
                    if (pos.liberties(pos.stringAt(n)).size == 1) captures = true
                }
                else -> {}
            }
        }
        return emptyNb > 0 || captures
    }

    private fun apply(
        pos: Position,
        action: LdrzAction,
        toPlay: StoneColor,
        passes: Int,
    ): Pair<Position, Int>? = when (action) {
        LdrzAction.Pass -> pos to passes + 1
        is LdrzAction.Move -> pos.play(action.point, toPlay)?.let { it to 0 }
    }

    private fun bump() {
        nodes++
        if ((nodes and 63) == 0) {
            emit("搜尋相關區（${currentZone.size} 點）　節點 $nodes")
        }
    }

    private fun expired(): Boolean {
        if (nodes >= nodeCap) return true
        return started.elapsedNow().inWholeMilliseconds >= timeLimitMs
    }

    private fun emit(phase: String) {
        onProgress(LdrzProgress(phase = phase, nodes = nodes, zone = currentZone))
    }

    private fun ttKey(pos: Position, toPlay: StoneColor, passes: Int): String =
        "${pos.key}|${toPlay.name}|$passes"

    private fun lineOf(point: Point?, rest: List<Point>): List<Point> =
        if (point == null) rest else listOf(point) + rest
}
