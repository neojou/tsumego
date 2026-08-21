package com.neojou.tsumego.solve

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.classify.bensonAlive

internal fun terminalRelevanceZone(
    position: Position,
    problem: Problem,
    outcome: Outcome,
    bothPassed: Boolean = false,
): Set<Point> {
    val whole = position.playable.toSet()
    return when (outcome) {
        Outcome.UnconditionalLive -> {
            val colors = problem.targets.mapNotNull { position.stones[it] }.toSet()
            val alive = colors.flatMap { bensonAlive(position, it) }.toSet()
            val zone = alive.toMutableSet()
            val seen = HashSet<Point>()
            for (p in alive) {
                if (!seen.add(p)) continue
                val string = position.stringAt(p)
                seen.addAll(string)
                zone.addAll(string)
                zone.addAll(position.liberties(string))
            }
            zone
        }
        Outcome.UnconditionalDead -> {
            val zone = LinkedHashSet<Point>()
            for (t in problem.targets) {
                zone.add(t)
                zone.addAll(position.neighbors(t))
                if (t in position.stones) {
                    val string = position.stringAt(t)
                    zone.addAll(string)
                    zone.addAll(position.liberties(string))
                    if (bothPassed) {
                        for (n in position.neighbors(t)) {
                            if (n in position.stones) zone.addAll(position.stringAt(n))
                        }
                    }
                }
            }
            zone
        }
        Outcome.Seki, Outcome.KoLive, Outcome.KoKill, Outcome.Unsettled -> whole
    }
}

internal fun dilate(zone: Set<Point>, position: Position, action: Action? = null): Set<Point> {
    if (zone.isEmpty()) return zone
    val out = LinkedHashSet(zone)
    if (action is Action.Move) out.add(action.point)
    for (p in out.toList()) {
        if (p in position.stones) out.addAll(position.stringAt(p))
    }
    for (p in out.toList()) {
        if (p in position.stones) out.addAll(position.liberties(position.stringAt(p)))
        out.addAll(position.neighbors(p))
    }
    return out.filter { position.rect.contains(it) }.toSet()
}

internal fun isNullMove(action: Action, zone: Set<Point>): Boolean {
    if (zone.isEmpty()) return false
    return action is Action.Move && action.point !in zone
}

internal fun retainMustPlay(
    listed: List<Action>,
    zone: Set<Point>,
    searched: Set<Action>,
): List<Action> {
    if (zone.isEmpty()) return listed.filter { it !in searched }
    return listed.filter { action ->
        action !in searched && (
            action is Action.Pass || (action is Action.Move && action.point in zone)
        )
    }
}

internal data class ZonePattern(
    val toPlay: StoneColor,
    val passes: Int,
    val zone: Set<Point>,
    val stones: Map<Point, StoneColor?>,
)

internal fun zonePattern(position: Position, toPlay: StoneColor, passes: Int, zone: Set<Point>): ZonePattern =
    ZonePattern(
        toPlay = toPlay,
        passes = passes,
        zone = zone,
        stones = zone.associateWith { position.stones[it] },
    )

internal fun zonePatternMatches(position: Position, pattern: ZonePattern): Boolean {
    if (pattern.zone.isEmpty()) return false
    return pattern.zone.all { point -> position.stones[point] == pattern.stones[point] }
}
