package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.classify.classify

internal const val LDRZ_DEFAULT_NODE_CAP = 20_000
private const val MAX_PLY = 48

internal data class LdrzSearchVal(
    val status: LdrzStatus,
    val line: List<Point>,
)

/**
 * Independent life/death search. Legal moves ⊆ region empties.
 * Does not read [LdrzProblem.answerFirstMove].
 */
internal class LdrzSearch(
    private val problem: LdrzProblem,
    private val nodeCap: Int,
) {
    private val attacker: StoneColor = problem.defenderColor().opposite
    private val defenderTargets: Set<Point> = problem.defenderTargets()
    private val region: Set<Point> = LdrzZone.points(problem)
    var nodes: Int = 0
        private set

    fun run(root: Position, toPlay: StoneColor): LdrzSearchVal {
        if (defenderTargets.isEmpty()) {
            return LdrzSearchVal(LdrzStatus.ERROR, emptyList())
        }
        return search(root, toPlay, lastPassed = false, ply = 0)
    }

    private fun evaluate(pos: Position, bothPassed: Boolean): LdrzStatus {
        if (defenderTargets.none { it in pos.stones }) return LdrzStatus.DEAD
        val outcome = classify(pos, defenderTargets, bothPassed)
        if (outcome == Outcome.UnconditionalLive) return LdrzStatus.ALIVE
        return LdrzStatus.UNSETTLED
    }

    private fun search(
        pos: Position,
        toPlay: StoneColor,
        lastPassed: Boolean,
        ply: Int,
    ): LdrzSearchVal {
        if (nodes >= nodeCap || ply >= MAX_PLY) {
            return LdrzSearchVal(LdrzStatus.UNSETTLED, emptyList())
        }
        nodes++

        val now = evaluate(pos, bothPassed = false)
        if (now != LdrzStatus.UNSETTLED) return LdrzSearchVal(now, emptyList())

        val moves = candidateMoves(pos, toPlay)
        if (moves.isEmpty()) {
            return passChild(pos, toPlay, lastPassed, ply)
        }

        val wantDead = toPlay == attacker
        var allAlive = true
        var allDead = true
        var truncated = false
        var unsettled: LdrzSearchVal? = null

        for (move in moves) {
            if (nodes >= nodeCap) {
                truncated = true
                break
            }
            val next = pos.play(move, toPlay) ?: continue
            val child = search(next, toPlay.opposite, lastPassed = false, ply = ply + 1)
            if (wantDead && child.status == LdrzStatus.DEAD) {
                return LdrzSearchVal(LdrzStatus.DEAD, listOf(move) + child.line)
            }
            if (!wantDead && child.status == LdrzStatus.ALIVE) {
                return LdrzSearchVal(LdrzStatus.ALIVE, listOf(move) + child.line)
            }
            if (child.status != LdrzStatus.ALIVE) allAlive = false
            if (child.status != LdrzStatus.DEAD) allDead = false
            if (child.status == LdrzStatus.UNSETTLED && unsettled == null) {
                unsettled = LdrzSearchVal(LdrzStatus.UNSETTLED, listOf(move) + child.line)
            }
        }

        if (nodes < nodeCap) {
            val child = passChild(pos, toPlay, lastPassed, ply)
            if (wantDead && child.status == LdrzStatus.DEAD) {
                return child
            }
            if (!wantDead && child.status == LdrzStatus.ALIVE) {
                return child
            }
            if (child.status != LdrzStatus.ALIVE) allAlive = false
            if (child.status != LdrzStatus.DEAD) allDead = false
            if (child.status == LdrzStatus.UNSETTLED && unsettled == null) {
                unsettled = child
            }
        } else {
            truncated = true
        }

        if (!truncated) {
            if (wantDead && allAlive) return LdrzSearchVal(LdrzStatus.ALIVE, emptyList())
            if (!wantDead && allDead) return LdrzSearchVal(LdrzStatus.DEAD, emptyList())
        }
        return unsettled
            ?: LdrzSearchVal(
                LdrzStatus.UNSETTLED,
                moves.firstOrNull()?.let { listOf(it) } ?: emptyList(),
            )
    }

    private fun passChild(
        pos: Position,
        toPlay: StoneColor,
        lastPassed: Boolean,
        ply: Int,
    ): LdrzSearchVal {
        if (lastPassed) {
            val after = evaluate(pos, bothPassed = true)
            return LdrzSearchVal(after, emptyList())
        }
        return search(pos, toPlay.opposite, lastPassed = true, ply = ply + 1)
    }

    private fun candidateMoves(pos: Position, toPlay: StoneColor): List<Point> {
        val liberties = LinkedHashSet<Point>()
        val seen = HashSet<Point>()
        for (t in defenderTargets) {
            if (t !in pos.stones || !seen.add(t)) continue
            val string = pos.stringAt(t)
            seen.addAll(string)
            liberties.addAll(pos.liberties(string))
        }
        val inRegionLibs = liberties
            .filter { it in region && it !in pos.stones }
            .sorted()
        val rest = region
            .filter { it !in pos.stones && it !in liberties }
            .sorted()
        return (inRegionLibs + rest).filter { pos.play(it, toPlay) != null }
    }
}
