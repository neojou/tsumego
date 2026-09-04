package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point

data class LdrzSession(
    val problem: LdrzProblem? = null,
    val calculating: Boolean = false,
    val result: LdrzResult? = null,
    val outputJson: String? = null,
    val outputSgf: String? = null,
    val showText: String? = null,
    val relevanceZone: Set<Point> = emptySet(),
) {
    val menuEnabled: Boolean get() = !calculating
}
