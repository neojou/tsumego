package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import kotlin.math.abs

fun completeLattice(peaks: List<Float>): List<Float> {
    val sorted = peaks.distinct().sorted()
    if (sorted.size < 2) return sorted
    val gaps = sorted.zipWithNext { a, b -> b - a }.sorted()
    val medianGap = gaps[gaps.size / 2]
    val typical = gaps.filter { it >= medianGap * 0.7f && it <= medianGap * 1.3f }
    val spacing = if (typical.isEmpty()) medianGap else typical[typical.size / 2]
    if (spacing <= 1f) return sorted

    var end = sorted.last()
    if (sorted.size >= 2 && end - sorted[sorted.lastIndex - 1] < spacing * 0.7f) {
        end = sorted[sorted.lastIndex - 1]
    }

    val lines = ArrayList<Float>()
    var x = sorted.first()
    val snap = spacing * 0.35f
    while (x <= end + snap) {
        val nearby = sorted.filter { abs(it - x) <= snap }
        val chosen = nearby.minByOrNull { abs(it - x) } ?: x
        if (lines.isEmpty() || chosen - lines.last() > spacing * 0.6f) {
            lines += chosen
        }
        x = (if (nearby.isNotEmpty()) chosen else x) + spacing
        if (lines.size >= 19) break
    }
    return lines
}

fun chooseLattices(
    xs: List<Float>,
    ys: List<Float>,
    imageWidth: Int,
    imageHeight: Int,
    crossing: (x: Float, y: Float) -> Float,
): Pair<List<Float>, List<Float>> {
    fun variants(lines: List<Float>): List<List<Float>> = buildList {
        add(lines)
        if (lines.size > 2) add(lines.drop(1))
        if (lines.size > 2) add(lines.dropLast(1))
    }

    fun outerStrength(lines: List<Float>, others: List<Float>, vertical: Boolean): Float {
        if (lines.isEmpty() || others.isEmpty()) return 0f
        return listOf(lines.first(), lines.last()).sumOf { pos ->
            others.map { other ->
                if (vertical) crossing(pos, other) else crossing(other, pos)
            }.average()
        }.toFloat()
    }

    fun score(xv: List<Float>, yv: List<Float>): Float {
        if (xv.size < 2 || yv.size < 2) return Float.NEGATIVE_INFINITY
        val spacingX = xv.zipWithNext { a, b -> b - a }.average().toFloat()
        val spacingY = yv.zipWithNext { a, b -> b - a }.average().toFloat()
        if (spacingX < 1f || spacingY < 1f) return Float.NEGATIVE_INFINITY
        val left = xv.first() / spacingX
        val right = (imageWidth - xv.last()) / spacingX
        val top = yv.first() / spacingY
        val bottom = (imageHeight - yv.last()) / spacingY
        val corner =
            (top >= 0.85f && right >= 0.85f) ||
                (top >= 0.85f && left >= 0.85f) ||
                (bottom >= 0.85f && right >= 0.85f) ||
                (bottom >= 0.85f && left >= 0.85f)
        val strength = outerStrength(xv, yv, vertical = true) + outerStrength(yv, xv, vertical = false)
        return (if (corner) 1000f else 0f) + strength + xv.size * yv.size * 40f
    }

    var best: Pair<List<Float>, List<Float>> = xs to ys
    var bestScore = Float.NEGATIVE_INFINITY
    for (xv in variants(xs)) {
        for (yv in variants(ys)) {
            val s = score(xv, yv)
            if (s > bestScore) {
                bestScore = s
                best = xv to yv
            }
        }
    }
    return best
}

fun assignCrop(
    fileCount: Int,
    rankCount: Int,
    marginLeft: Float,
    marginRight: Float,
    marginTop: Float,
    marginBottom: Float,
): Pair<BoardRect, Edges> {
    val leftReal = marginLeft >= 0.85f
    val rightReal = marginRight >= 0.85f
    val topReal = marginTop >= 0.85f
    val bottomReal = marginBottom >= 0.85f
    val files = fileCount.coerceIn(2, 19)
    val ranks = rankCount.coerceIn(2, 19)
    val rect = when {
        rightReal && topReal -> BoardRect(
            left = (18 - files + 1).coerceAtLeast(0),
            right = 18,
            bottom = (19 - ranks + 1).coerceAtLeast(1),
            top = 19,
        )
        leftReal && bottomReal -> BoardRect(
            left = 0,
            right = files - 1,
            bottom = 1,
            top = ranks,
        )
        leftReal && topReal -> BoardRect(
            left = 0,
            right = files - 1,
            bottom = (20 - ranks).coerceAtLeast(1),
            top = 19,
        )
        rightReal && bottomReal -> BoardRect(
            left = (19 - files).coerceAtLeast(0),
            right = 18,
            bottom = 1,
            top = ranks,
        )
        else -> BoardRect(
            left = 0,
            right = files - 1,
            bottom = 1,
            top = ranks,
        )
    }
    val edges = Edges(
        left = if (leftReal) EdgeKind.Real else EdgeKind.Wall,
        right = if (rightReal) EdgeKind.Real else EdgeKind.Wall,
        bottom = if (bottomReal) EdgeKind.Real else EdgeKind.Wall,
        top = if (topReal) EdgeKind.Real else EdgeKind.Wall,
    )
    return rect to edges
}
