package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.Edges
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs

data class DiagramLayout(
    val rect: BoardRect,
    val edges: Edges,
    val imageGrid: ImageGrid,
)

fun detectDiagramLayout(bytes: ByteArray): DiagramLayout? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    val width = image.width
    val height = image.height
    if (width < 32 || height < 32) return@runCatching null
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.UNPREMUL))
    if (!image.readPixels(bitmap)) return@runCatching null
    val pix = bitmap.peekPixels() ?: return@runCatching null

    fun lum(x: Int, y: Int): Float {
        val c = pix.getColor(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    val xs = completeLattice(linePeaks(width, height, vertical = true, lum = ::lum))
    val ys = completeLattice(linePeaks(width, height, vertical = false, lum = ::lum))
    if (xs.size < 2 || ys.size < 2) return@runCatching null
    val (chosenX, chosenY) = chooseLattices(xs, ys, width, height) { x, y ->
        val xi = x.toInt().coerceIn(1, width - 2)
        val yi = y.toInt().coerceIn(1, height - 2)
        val gx = abs(lum(xi + 1, yi) - lum(xi - 1, yi))
        val gy = abs(lum(xi, yi + 1) - lum(xi, yi - 1))
        minOf(gx, gy)
    }

    val spacingX = chosenX.zipWithNext { a, b -> b - a }.average().toFloat()
    val spacingY = chosenY.zipWithNext { a, b -> b - a }.average().toFloat()
    if (spacingX < 4f || spacingY < 4f) return@runCatching null

    val (rect, edges) = assignCrop(
        fileCount = chosenX.size,
        rankCount = chosenY.size,
        marginLeft = chosenX.first() / spacingX,
        marginRight = (width - chosenX.last()) / spacingX,
        marginTop = chosenY.first() / spacingY,
        marginBottom = (height - chosenY.last()) / spacingY,
    )
    val imageGrid = ImageGrid(
        left = chosenX.first() / width,
        top = chosenY.first() / height,
        right = chosenX.last() / width,
        bottom = chosenY.last() / height,
    )
    DiagramLayout(rect, edges, imageGrid)
}.getOrNull()

private fun linePeaks(
    width: Int,
    height: Int,
    vertical: Boolean,
    lum: (Int, Int) -> Float,
): List<Float> {
    val len = if (vertical) width else height
    val energy = FloatArray(len)
    val x0 = 2
    val x1 = width - 3
    val y0 = 2
    val y1 = height - 3
    if (vertical) {
        for (y in y0..y1) {
            for (x in x0..x1) {
                val gx = lum(x + 1, y) - lum(x - 1, y)
                val gy = lum(x, y + 1) - lum(x, y - 1)
                val e = abs(gx) - 0.3f * abs(gy)
                if (e > 8f) energy[x] += e
            }
        }
    } else {
        for (y in y0..y1) {
            for (x in x0..x1) {
                val gx = lum(x + 1, y) - lum(x - 1, y)
                val gy = lum(x, y + 1) - lum(x, y - 1)
                val e = abs(gy) - 0.3f * abs(gx)
                if (e > 8f) energy[y] += e
            }
        }
    }
    val smooth = FloatArray(len)
    for (i in energy.indices) {
        var s = 0f
        var n = 0
        for (j in (i - 2)..(i + 2)) {
            if (j in energy.indices) {
                s += energy[j]
                n++
            }
        }
        smooth[i] = s / n
    }
    val max = smooth.max()
    if (max <= 0f) return emptyList()
    val raw = ArrayList<Pair<Int, Float>>()
    for (i in 3 until len - 3) {
        val v = smooth[i]
        if (v < max * 0.25f) continue
        if (v >= smooth[i - 1] && v >= smooth[i + 1] &&
            v >= smooth[i - 2] && v >= smooth[i + 2] &&
            v >= smooth[i - 3] && v >= smooth[i + 3]
        ) {
            raw += i to v
        }
    }
    if (raw.isEmpty()) return emptyList()
    val groups = ArrayList<MutableList<Pair<Int, Float>>>()
    for (p in raw) {
        val last = groups.lastOrNull()
        if (last == null || p.first - last.last().first > 10) {
            groups += mutableListOf(p)
        } else {
            last += p
        }
    }
    return groups.map { g -> g.maxBy { it.second }.first.toFloat() }
}
