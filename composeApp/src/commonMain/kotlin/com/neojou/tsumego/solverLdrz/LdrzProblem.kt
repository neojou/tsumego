package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor

enum class LdrzGoal {
    TOLIVE,
    TOKILL,
}

data class LdrzProblem(
    val stem: String,
    val sourceJsonName: String,
    val filename: String,
    val category: String,
    val turnColor: StoneColor,
    val winningColor: StoneColor?,
    val blackCrucial: Set<Point>,
    val whiteCrucial: Set<Point>,
    val blackGoal: LdrzGoal,
    val whiteGoal: LdrzGoal,
    val answerFirstMove: String?,
    val region: Set<Point>,
    val stones: Map<Point, StoneColor>,
    val boardSize: Int = 19,
) {
    fun geometry(): Pair<BoardRect, Edges> = boardGeometry(stones.keys + region, boardSize)

    fun toPosition(): Position {
        val (rect, edges) = geometry()
        val onBoard = stones.filterKeys { rect.contains(it) }
        val key = Position.stoneKey(rect, onBoard)
        return Position(rect, edges, onBoard, setOf(key))
    }

    /**
     * Local 題目盤 for search: bounding box of a 1-dilate around 關鍵子,
     * 牆 on sides that are not the 19-road 真盤邊. Stops the automask skirt
     * from leaking liberties so Benson UCA can fire.
     */
    fun toSearchPosition(): Position {
        val full = toPosition()
        val core = LdrzZone.seed(this, full, dilateTimes = 1)
        val (rect, edges) = boardGeometry(core, boardSize)
        val onBoard = stones.filterKeys { rect.contains(it) }
        val key = Position.stoneKey(rect, onBoard)
        return Position(rect, edges, onBoard, setOf(key))
    }

    fun crucialStones(): Set<Point> = blackCrucial + whiteCrucial

    fun regionMarks(): Set<Point> = region.filter { it !in stones }.toSet()

    fun turnWire(): String = if (turnColor == StoneColor.Black) "b" else "w"

    fun defenderColor(): StoneColor {
        val blackLive = blackGoal == LdrzGoal.TOLIVE
        val whiteLive = whiteGoal == LdrzGoal.TOLIVE
        val blackKill = blackGoal == LdrzGoal.TOKILL
        val whiteKill = whiteGoal == LdrzGoal.TOKILL
        return when {
            blackLive && !whiteLive -> StoneColor.Black
            whiteLive && !blackLive -> StoneColor.White
            blackKill && !whiteKill -> StoneColor.White
            whiteKill && !blackKill -> StoneColor.Black
            blackCrucial.isNotEmpty() -> StoneColor.Black
            else -> StoneColor.White
        }
    }

    fun defenderTargets(): Set<Point> =
        if (defenderColor() == StoneColor.Black) blackCrucial else whiteCrucial
}

internal fun boardGeometry(points: Set<Point>, boardSize: Int): Pair<BoardRect, Edges> {
    val size = boardSize.coerceIn(1, 19)
    if (points.isEmpty()) {
        val rect = BoardRect(left = 0, right = size - 1, bottom = 1, top = size)
        val edges = Edges(EdgeKind.Real, EdgeKind.Real, EdgeKind.Real, EdgeKind.Real)
        return rect to edges
    }
    val left = points.minOf { it.file }
    val right = points.maxOf { it.file }
    val bottom = points.minOf { it.rank }
    val top = points.maxOf { it.rank }
    val rect = BoardRect(left, right, bottom, top)
    val edges = Edges(
        left = if (left == 0) EdgeKind.Real else EdgeKind.Wall,
        right = if (right == size - 1) EdgeKind.Real else EdgeKind.Wall,
        bottom = if (bottom == 1) EdgeKind.Real else EdgeKind.Wall,
        top = if (top == size) EdgeKind.Real else EdgeKind.Wall,
    )
    return rect to edges
}
