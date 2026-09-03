package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point

/**
 * v1 zone is the JSON [LdrzProblem.region] clipped to the 題目盤 rectangle.
 * (Not the trainer RelevanceZone, not the C++ R-zone.)
 */
object LdrzZone {
    fun points(problem: LdrzProblem): Set<Point> {
        val (rect, _) = problem.geometry()
        return problem.region.filter { rect.contains(it) }.toSet()
    }
}
