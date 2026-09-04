package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.classify.bensonAlive

/**
 * Relevance-Zone helpers for study-LD-RZ (Shih et al. RZS).
 *
 * JSON `region` is only the automask whitelist. The R-zone is grown from
 * UCA / capture terminals and dilated so a winning reply can be replayed.
 */
object LdrzZone {
    fun playableRegion(problem: LdrzProblem): Set<Point> {
        val (rect, _) = problem.geometry()
        return problem.region.filter { rect.contains(it) }.toSet()
    }

    /** Kishimoto-style local shade: crucial strings dilated, clipped to JSON region. */
    fun seed(problem: LdrzProblem, pos: Position, dilateTimes: Int = 3): Set<Point> {
        val whitelist = playableRegion(problem)
        val start = LinkedHashSet<Point>()
        val seen = HashSet<Point>()
        for (t in problem.defenderTargets()) {
            if (t in pos.stones) {
                if (!seen.add(t)) continue
                val string = pos.stringAt(t)
                seen.addAll(string)
                start.addAll(string)
                start.addAll(pos.liberties(string))
            } else {
                start.add(t)
                start.addAll(pos.neighbors(t))
            }
        }
        var zone: Set<Point> = start
        repeat(dilateTimes) { zone = dilate(zone, pos) }
        return zone.filter { it in whitelist }.toSet()
    }

    fun terminalAlive(pos: Position, defender: StoneColor, crucial: Set<Point>): Set<Point> {
        val alive = bensonAlive(pos, defender)
        val zone = LinkedHashSet<Point>()
        val seen = HashSet<Point>()
        for (t in crucial) {
            if (t !in pos.stones || t !in alive || !seen.add(t)) continue
            val string = pos.stringAt(t)
            seen.addAll(string)
            zone.addAll(string)
            zone.addAll(pos.liberties(string))
        }
        return zone
    }

    fun terminalDead(pos: Position, crucial: Set<Point>): Set<Point> {
        val zone = LinkedHashSet<Point>()
        for (t in crucial) {
            zone.add(t)
            zone.addAll(pos.neighbors(t))
            if (t in pos.stones) {
                val string = pos.stringAt(t)
                zone.addAll(string)
                zone.addAll(pos.liberties(string))
            }
        }
        return zone
    }

    fun dilate(zone: Set<Point>, pos: Position, move: Point? = null): Set<Point> {
        if (zone.isEmpty() && move == null) return zone
        val out = LinkedHashSet(zone)
        if (move != null) out.add(move)
        for (p in out.toList()) {
            if (p in pos.stones) out.addAll(pos.stringAt(p))
        }
        for (p in out.toList()) {
            if (p in pos.stones) out.addAll(pos.liberties(pos.stringAt(p)))
            out.addAll(pos.neighbors(p))
        }
        return out.filter { pos.rect.contains(it) }.toSet()
    }

    fun isNullMove(point: Point?, zone: Set<Point>): Boolean {
        if (zone.isEmpty() || point == null) return false
        return point !in zone
    }

    /**
     * Liberties of the crucial strings plus one extra empty ring
     * (classic 做眼空間). This is the move generator, not the JSON region.
     */
    fun searchPoints(pos: Position, crucial: Set<Point>, whitelist: Set<Point>): Set<Point> {
        val onBoard = crucial.filter { it in pos.stones }.toSet()
        if (onBoard.isEmpty()) {
            return crucial.flatMap { pos.neighbors(it) }.filter { it in whitelist && it !in pos.stones }.toSet()
        }
        val group = LinkedHashSet<Point>()
        val seen = HashSet<Point>()
        for (t in onBoard) {
            if (!seen.add(t)) continue
            val string = pos.stringAt(t)
            seen.addAll(string)
            group.addAll(string)
        }
        val region = LinkedHashSet<Point>()
        for (p in group) {
            for (n in pos.neighbors(p)) {
                if (n !in pos.stones) region.add(n)
            }
        }
        for (lib in region.toList()) {
            for (n in pos.neighbors(lib)) {
                if (n !in pos.stones) region.add(n)
            }
        }
        return region.filter { it in whitelist }.toSet()
    }

    fun mustPlay(listed: List<LdrzAction>, zone: Set<Point>, searched: Set<LdrzAction>): List<LdrzAction> {
        if (zone.isEmpty()) return listed.filter { it !in searched }
        return listed.filter { action ->
            action !in searched && action is LdrzAction.Move && action.point in zone
        }
    }
}

sealed class LdrzAction {
    data class Move(val point: Point) : LdrzAction()
    data object Pass : LdrzAction()
}

data class LdrzProgress(
    val phase: String,
    val nodes: Int = 0,
    val depth: Int = 0,
    val zone: Set<Point> = emptySet(),
)
