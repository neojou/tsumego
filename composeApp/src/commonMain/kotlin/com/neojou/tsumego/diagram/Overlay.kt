package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.Point
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the 題目盤 lattice sits inside the source image, as fractions of width/height.
 * (0, 0) is the image top-left; [top] < [bottom] because image y grows downward.
 */
data class ImageGrid(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(right > left && bottom > top) { "image grid $left,$top,$right,$bottom" }
    }
}

data class ScreenPos(val x: Float, val y: Float)

data class OverlayLayout(
    val imageLeft: Float,
    val imageTop: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val gridLeft: Float,
    val gridTop: Float,
    val spacingX: Float,
    val spacingY: Float,
    val rect: BoardRect,
) {
    fun center(point: Point): ScreenPos = ScreenPos(
        x = gridLeft + (point.file - rect.left) * spacingX,
        y = gridTop + (rect.top - point.rank) * spacingY,
    )

    fun hit(x: Float, y: Float): Point? {
        if (spacingX <= 0f || spacingY <= 0f) return null
        val file = rect.left + ((x - gridLeft) / spacingX).roundToInt()
        val rank = rect.top - ((y - gridTop) / spacingY).roundToInt()
        val point = runCatching { Point(file, rank) }.getOrNull() ?: return null
        if (!rect.contains(point)) return null
        val c = center(point)
        val dx = c.x - x
        val dy = c.y - y
        val reach = min(spacingX, spacingY) * 0.45f
        return if (dx * dx + dy * dy <= reach * reach) point else null
    }
}

fun overlayLayout(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    imageGrid: ImageGrid,
    rect: BoardRect,
): OverlayLayout {
    val scale = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
    val drawnW = imageWidth * scale
    val drawnH = imageHeight * scale
    val imageLeft = (canvasWidth - drawnW) / 2f
    val imageTop = (canvasHeight - drawnH) / 2f
    val files = (rect.right - rect.left).coerceAtLeast(1)
    val ranks = (rect.top - rect.bottom).coerceAtLeast(1)
    val gridLeft = imageLeft + imageGrid.left * drawnW
    val gridTop = imageTop + imageGrid.top * drawnH
    val gridRight = imageLeft + imageGrid.right * drawnW
    val gridBottom = imageTop + imageGrid.bottom * drawnH
    return OverlayLayout(
        imageLeft = imageLeft,
        imageTop = imageTop,
        imageWidth = drawnW,
        imageHeight = drawnH,
        gridLeft = gridLeft,
        gridTop = gridTop,
        spacingX = (gridRight - gridLeft) / files,
        spacingY = (gridBottom - gridTop) / ranks,
        rect = rect,
    )
}
