package com.neojou.tsumego.diagram.desktop

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.diagram.ConfirmDraft
import com.neojou.tsumego.diagram.DiagramReader
import com.neojou.tsumego.diagram.emptyDraft
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs

/**
 * Best-effort fill for aligned printed diagrams. Failure returns an empty draft
 * with the original image; the confirm screen still works.
 */
class DesktopDiagramReader : DiagramReader {
    override fun read(image: ByteArray, diagramFirst: StoneColor): ConfirmDraft {
        return runCatching { recognize(image) }.getOrNull() ?: emptyDraft(image)
    }
}

private data class Blob(val x: Float, val y: Float, val color: StoneColor, val radius: Float)

private fun recognize(bytes: ByteArray): ConfirmDraft? {
    val image = Image.makeFromEncoded(bytes)
    val w = image.width
    val h = image.height
    if (w < 32 || h < 32) return null
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.UNPREMUL))
    if (!image.readPixels(bitmap)) return null
    val pix = bitmap.peekPixels() ?: return null

    fun argb(x: Int, y: Int): Int = pix.getColor(x, y)
    fun lum(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    var woodMinX = w
    var woodMaxX = 0
    var woodMinY = h
    var woodMaxY = 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            val c = argb(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = (c shr 0) and 0xFF
            val isWood = r > 140 && g > 100 && b < 140 && r > b + 20
            if (isWood) {
                if (x < woodMinX) woodMinX = x
                if (x > woodMaxX) woodMaxX = x
                if (y < woodMinY) woodMinY = y
                if (y > woodMaxY) woodMaxY = y
            }
        }
    }
    if (woodMaxX <= woodMinX || woodMaxY <= woodMinY) return null

    val visited = BooleanArray(w * h)
    val blobs = ArrayList<Blob>()
    val minArea = ((w * h) / 8000).coerceIn(20, 400)
    val maxArea = ((w * h) / 80).coerceIn(200, 20_000)

    fun collect(lumaMin: Int, lumaMax: Int, color: StoneColor) {
        for (y in woodMinY..woodMaxY) {
            for (x in woodMinX..woodMaxX) {
                val i = y * w + x
                if (visited[i]) continue
                val l = lum(argb(x, y))
                if (l < lumaMin || l > lumaMax) continue
                var count = 0
                var sx = 0
                var sy = 0
                val stack = ArrayDeque<Int>()
                stack.add(i)
                visited[i] = true
                while (stack.isNotEmpty()) {
                    val cur = stack.removeLast()
                    val cx = cur % w
                    val cy = cur / w
                    count++
                    sx += cx
                    sy += cy
                    val neigh = intArrayOf(cx - 1, cy, cx + 1, cy, cx, cy - 1, cx, cy + 1)
                    var k = 0
                    while (k < neigh.size) {
                        val nx = neigh[k]
                        val ny = neigh[k + 1]
                        k += 2
                        if (nx < woodMinX || nx > woodMaxX || ny < woodMinY || ny > woodMaxY) continue
                        val ni = ny * w + nx
                        if (visited[ni]) continue
                        val nl = lum(argb(nx, ny))
                        if (nl < lumaMin || nl > lumaMax) continue
                        visited[ni] = true
                        stack.add(ni)
                    }
                }
                if (count in minArea..maxArea) {
                    val radius = kotlin.math.sqrt(count / Math.PI).toFloat()
                    blobs += Blob(sx / count.toFloat(), sy / count.toFloat(), color, radius)
                }
            }
        }
    }

    collect(0, 70, StoneColor.Black)
    collect(185, 255, StoneColor.White)
    if (blobs.size < 3) return null

    val xs = cluster(blobs.map { it.x })
    val ys = cluster(blobs.map { it.y })
    if (xs.size < 2 || ys.size < 2) return null
    val spacingX = medianGap(xs) ?: return null
    val spacingY = medianGap(ys) ?: return null
    val spacing = (spacingX + spacingY) / 2f

    val gridXs = fillGrid(xs, spacing, woodMinX.toFloat(), woodMaxX.toFloat())
    val gridYs = fillGrid(ys, spacing, woodMinY.toFloat(), woodMaxY.toFloat())
    if (gridXs.size !in 2..19 || gridYs.size !in 2..19) return null

    val near = spacing * 0.55f
    val rightReal = abs(gridXs.last() - woodMaxX) < spacing * 1.2f
    val leftReal = abs(gridXs.first() - woodMinX) < spacing * 1.2f
    val topReal = abs(gridYs.first() - woodMinY) < spacing * 1.2f
    val bottomReal = abs(gridYs.last() - woodMaxY) < spacing * 1.2f

    val files = gridXs.size
    val ranks = gridYs.size
    val leftFile = when {
        leftReal -> 0
        rightReal -> (18 - files + 1).coerceAtLeast(0)
        else -> 0
    }
    val rightFile = leftFile + files - 1
    val topRank = when {
        topReal -> 19
        bottomReal -> ranks
        else -> ranks
    }
    val bottomRank = topRank - ranks + 1

    val rect = BoardRect(leftFile, rightFile.coerceAtMost(18), bottomRank.coerceAtLeast(1), topRank.coerceAtMost(19))
    val edges = Edges(
        left = if (leftReal) EdgeKind.Real else EdgeKind.Wall,
        right = if (rightReal) EdgeKind.Real else EdgeKind.Wall,
        bottom = if (bottomReal) EdgeKind.Real else EdgeKind.Wall,
        top = if (topReal) EdgeKind.Real else EdgeKind.Wall,
    )

    val stones = LinkedHashMap<Point, StoneColor>()
    for (blob in blobs) {
        val fi = nearestIndex(gridXs, blob.x, near) ?: continue
        val ri = nearestIndex(gridYs, blob.y, near) ?: continue
        val file = leftFile + fi
        val rank = topRank - ri
        if (file in 0..18 && rank in 1..19) {
            stones[Point(file, rank)] = blob.color
        }
    }
    if (stones.size < 3) return null
    return ConfirmDraft(imageBytes = bytes, rect = rect, edges = edges, stones = stones)
}

private fun cluster(values: List<Float>): List<Float> {
    val sorted = values.sorted()
    val groups = ArrayList<MutableList<Float>>()
    for (v in sorted) {
        val last = groups.lastOrNull()
        if (last == null || abs(last.average() - v) > 8f) {
            groups += mutableListOf(v)
        } else {
            last += v
        }
    }
    return groups.map { it.average().toFloat() }
}

private fun medianGap(values: List<Float>): Float? {
    if (values.size < 2) return null
    val gaps = values.zipWithNext { a, b -> b - a }.filter { it > 4f }.sorted()
    if (gaps.isEmpty()) return null
    return gaps[gaps.size / 2]
}

private fun fillGrid(occupied: List<Float>, spacing: Float, min: Float, max: Float): List<Float> {
    val start = occupied.first()
    val out = ArrayList<Float>()
    var x = start
    while (x > min + spacing * 0.4f) {
        x -= spacing
        if (x >= min - spacing * 0.2f) out.add(0, x)
    }
    out.add(start)
    x = start
    while (x < max - spacing * 0.4f) {
        x += spacing
        if (x <= max + spacing * 0.2f) out.add(x)
    }
    val unique = ArrayList<Float>()
    for (v in out.sorted()) {
        if (unique.isEmpty() || abs(unique.last() - v) > spacing * 0.4f) unique += v
    }
    return unique
}

private fun nearestIndex(grid: List<Float>, value: Float, near: Float): Int? {
    var best = 0
    var bestD = Float.MAX_VALUE
    for (i in grid.indices) {
        val d = abs(grid[i] - value)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return if (bestD <= near) best else null
}
