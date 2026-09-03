package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point

enum class LdrzStatus {
    ALIVE,
    DEAD,
    UNSETTLED,
    ERROR,
}

data class LdrzResult(
    val status: LdrzStatus,
    val firstMove: Point? = null,
    val firstMoveSgf: String? = null,
    val numSimulations: Int = 0,
    val timeSeconds: Double = 0.0,
    val zoneCount: Int = 0,
    val principalLine: List<Point> = emptyList(),
    val message: String = "",
)

interface LdrzLifeDeathSolver {
    fun solve(problem: LdrzProblem): LdrzResult
}
