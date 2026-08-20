package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.Problem
import com.neojou.tsumego.board.StoneColor

data class ConfirmDraft(
    val imageBytes: ByteArray?,
    val rect: BoardRect,
    val edges: Edges,
    val stones: Map<Point, StoneColor>,
    val goal: Goal = Goal.Live,
    val targets: Set<Point> = emptySet(),
    val imageGrid: ImageGrid? = null,
) {
    fun cycleStone(point: Point): ConfirmDraft {
        if (!rect.contains(point)) return this
        val next = when (stones[point]) {
            null -> StoneColor.Black
            StoneColor.Black -> StoneColor.White
            StoneColor.White -> null
        }
        val newStones = if (next == null) stones - point else stones + (point to next)
        val newTargets = if (next == null) targets - point else targets
        return copy(stones = newStones, targets = newTargets)
    }

    fun toggleTarget(point: Point): ConfirmDraft {
        if (stones[point] == null) return this
        if (point in targets) return copy(targets = targets - point)
        val position = Position(rect, edges, stones, past = emptySet())
        return copy(targets = targets + position.stringAt(point))
    }

    fun applyClick(point: Point, targetMode: Boolean): ConfirmDraft =
        if (targetMode) toggleTarget(point) else cycleStone(point)

    fun withRect(newRect: BoardRect): ConfirmDraft {
        val newStones = stones.filterKeys { newRect.contains(it) }
        val newTargets = targets.filter { newRect.contains(it) && it in newStones }.toSet()
        return copy(rect = newRect, stones = newStones, targets = newTargets)
    }

    fun flipped(): ConfirmDraft = copy(
        stones = stones.mapValues { it.value.opposite },
    )

    fun validationError(): String? = toProblem().validationError()

    fun toProblem(): Problem = Problem(
        rect = rect,
        edges = edges,
        stones = stones,
        goal = goal,
        targets = targets,
    )
}

fun interface DiagramReader {
    fun read(image: ByteArray, diagramFirst: StoneColor): ConfirmDraft
}

object EmptyDiagramReader : DiagramReader {
    override fun read(image: ByteArray, diagramFirst: StoneColor): ConfirmDraft =
        emptyDraft(image)
}

fun emptyDraft(image: ByteArray?): ConfirmDraft {
    if (image != null) {
        val layout = detectDiagramLayout(image)
        if (layout != null) {
            return ConfirmDraft(
                imageBytes = image,
                rect = layout.rect,
                edges = layout.edges,
                stones = layout.stones,
                imageGrid = layout.imageGrid,
            )
        }
    }
    return ConfirmDraft(
        imageBytes = image,
        rect = BoardRect(left = 0, right = 18, bottom = 1, top = 19),
        edges = Edges(
            left = EdgeKind.Real,
            right = EdgeKind.Real,
            bottom = EdgeKind.Real,
            top = EdgeKind.Real,
        ),
        stones = emptyMap(),
    )
}

fun readDiagram(reader: DiagramReader, image: ByteArray, diagramFirst: StoneColor): ConfirmDraft {
    val draft = reader.read(image, diagramFirst)
    return if (diagramFirst == StoneColor.White) draft.flipped() else draft
}
