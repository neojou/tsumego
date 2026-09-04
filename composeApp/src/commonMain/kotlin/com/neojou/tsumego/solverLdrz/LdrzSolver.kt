package com.neojou.tsumego.solverLdrz

import kotlin.time.TimeSource

/**
 * Independent Kotlin life/death engine for the study-LD-RZ menu.
 * Concepts: Shih et al., IEEE ToG / arXiv:2512.21365 — RZS, no C++ / Caffe2 / JNI.
 */
class LdrzSolver(
    private val nodeCap: Int = LDRZ_DEFAULT_NODE_CAP,
    private val timeLimitMs: Long = LDRZ_DEFAULT_TIME_MS,
    private val onProgress: (LdrzProgress) -> Unit = {},
) : LdrzLifeDeathSolver {
    override fun solve(problem: LdrzProblem): LdrzResult {
        val started = TimeSource.Monotonic.markNow()
        val targets = problem.defenderTargets()
        if (targets.isEmpty()) {
            onProgress(LdrzProgress("沒有關鍵子"))
            return LdrzResult(
                status = LdrzStatus.ERROR,
                timeSeconds = elapsedSeconds(started),
                message = "沒有關鍵子",
            )
        }
        val root = problem.toSearchPosition()
        val search = LdrzSearch(problem, nodeCap, timeLimitMs, onProgress)
        val found = search.run(root, problem.turnColor)
        val first = found.line.firstOrNull()
        val zone = found.zone.ifEmpty { search.currentZone }
        val timedOut = search.nodes >= nodeCap ||
            started.elapsedNow().inWholeMilliseconds >= timeLimitMs
        val status = when {
            found.status == LdrzStatus.ERROR -> LdrzStatus.ERROR
            found.status == LdrzStatus.UNSETTLED && timedOut -> LdrzStatus.UNSETTLED
            else -> found.status
        }
        val message = when {
            status == LdrzStatus.UNSETTLED && search.nodes >= nodeCap -> "節點上限 $nodeCap"
            status == LdrzStatus.UNSETTLED && timedOut -> "時間上限"
            else -> ""
        }
        return LdrzResult(
            status = status,
            firstMove = first,
            firstMoveSgf = first?.let { LdrzCoord.toSgf(it, problem.boardSize) },
            numSimulations = search.nodes,
            timeSeconds = elapsedSeconds(started),
            zoneCount = zone.size,
            zone = zone,
            principalLine = found.line,
            message = message,
        )
    }
}

private fun elapsedSeconds(started: TimeSource.Monotonic.ValueTimeMark): Double {
    val ms = started.elapsedNow().inWholeMilliseconds
    return ms / 1000.0
}
