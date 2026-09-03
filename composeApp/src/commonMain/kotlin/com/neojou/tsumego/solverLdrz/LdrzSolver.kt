package com.neojou.tsumego.solverLdrz

import kotlin.time.TimeSource

/**
 * Independent Kotlin life/death engine for the study-LD-RZ menu.
 * Concepts: Shih et al., IEEE ToG / arXiv:2512.21365 — no C++ / Caffe2 / JNI.
 */
class LdrzSolver(
    private val nodeCap: Int = LDRZ_DEFAULT_NODE_CAP,
) : LdrzLifeDeathSolver {
    override fun solve(problem: LdrzProblem): LdrzResult {
        val started = TimeSource.Monotonic.markNow()
        val zoneCount = LdrzZone.points(problem).size
        val targets = problem.defenderTargets()
        if (targets.isEmpty()) {
            return LdrzResult(
                status = LdrzStatus.ERROR,
                timeSeconds = elapsedSeconds(started),
                zoneCount = zoneCount,
                message = "沒有關鍵子",
            )
        }
        val root = problem.toPosition()
        val search = LdrzSearch(problem, nodeCap)
        val found = search.run(root, problem.turnColor)
        val first = found.line.firstOrNull()
        val status =
            if (found.status == LdrzStatus.ERROR) LdrzStatus.ERROR
            else if (search.nodes >= nodeCap && found.status == LdrzStatus.UNSETTLED) LdrzStatus.UNSETTLED
            else found.status
        return LdrzResult(
            status = status,
            firstMove = first,
            firstMoveSgf = first?.let { LdrzCoord.toSgf(it, problem.boardSize) },
            numSimulations = search.nodes,
            timeSeconds = elapsedSeconds(started),
            zoneCount = zoneCount,
            principalLine = found.line,
            message = if (status == LdrzStatus.UNSETTLED && search.nodes >= nodeCap) {
                "節點上限 $nodeCap"
            } else {
                ""
            },
        )
    }
}

private fun elapsedSeconds(started: TimeSource.Monotonic.ValueTimeMark): Double {
    val ms = started.elapsedNow().inWholeMilliseconds
    return ms / 1000.0
}
