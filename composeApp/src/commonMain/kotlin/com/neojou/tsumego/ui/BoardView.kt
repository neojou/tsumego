package com.neojou.tsumego.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.diagram.ImageGrid
import com.neojou.tsumego.diagram.OverlayLayout
import com.neojou.tsumego.diagram.overlayLayout
import org.jetbrains.skia.Image
import kotlin.math.min

fun decodeBoardImage(bytes: ByteArray): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

private data class BoardGeom(
    val spacing: Float,
    val origin: Offset,
    val rect: BoardRect,
) {
    fun center(point: Point): Offset = Offset(
        x = origin.x + (point.file - rect.left) * spacing,
        y = origin.y + (rect.top - point.rank) * spacing,
    )

    fun hit(position: Offset): Point? {
        val file = rect.left + ((position.x - origin.x) / spacing).let { kotlin.math.round(it).toInt() }
        val rank = rect.top - ((position.y - origin.y) / spacing).let { kotlin.math.round(it).toInt() }
        val point = runCatching { Point(file, rank) }.getOrNull() ?: return null
        if (!rect.contains(point)) return null
        val c = center(point)
        val dx = c.x - position.x
        val dy = c.y - position.y
        return if (dx * dx + dy * dy <= (spacing * 0.45f) * (spacing * 0.45f)) point else point
    }
}

@Composable
fun BoardView(
    rect: BoardRect,
    edges: Edges,
    stones: Map<Point, StoneColor>,
    targets: Set<Point> = emptySet(),
    lastMove: Point? = null,
    overlayImage: ImageBitmap? = null,
    imageGrid: ImageGrid? = null,
    enabled: Boolean = true,
    onClick: (Point) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val clickKey = remember(rect, enabled, imageGrid, overlayImage?.width, overlayImage?.height) {
        listOf(rect, enabled, imageGrid, overlayImage?.width, overlayImage?.height)
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(clickKey) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    hitOnBoard(
                        canvasWidth = size.width.toFloat(),
                        canvasHeight = size.height.toFloat(),
                        rect = rect,
                        overlayImage = overlayImage,
                        imageGrid = imageGrid,
                        x = offset.x,
                        y = offset.y,
                    )?.let(onClick)
                }
            },
    ) {
        val image = overlayImage
        val overlay = image?.let { bmp ->
            overlayLayout(
                canvasWidth = size.width,
                canvasHeight = size.height,
                imageWidth = bmp.width.toFloat(),
                imageHeight = bmp.height.toFloat(),
                imageGrid = imageGrid ?: ImageGrid(0f, 0f, 1f, 1f),
                rect = rect,
            )
        }
        if (image != null && overlay != null) {
            drawImage(
                image = image,
                dstSize = androidx.compose.ui.unit.IntSize(
                    overlay.imageWidth.toInt().coerceAtLeast(1),
                    overlay.imageHeight.toInt().coerceAtLeast(1),
                ),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    overlay.imageLeft.toInt(),
                    overlay.imageTop.toInt(),
                ),
                alpha = 1f,
            )
            drawOverlayGrid(overlay, edges)
            drawOverlayCoordinates(overlay, measurer)
            val spacing = min(overlay.spacingX, overlay.spacingY)
            for (point in targets) {
                val c = overlay.center(point)
                drawCircle(Color(0x66C62828), radius = spacing * 0.22f, center = Offset(c.x, c.y))
            }
            for ((point, color) in stones) {
                val c = overlay.center(point)
                drawStone(Offset(c.x, c.y), spacing * 0.46f, color)
            }
            if (lastMove != null && lastMove in stones) {
                val c = overlay.center(lastMove)
                val mark = if (stones[lastMove] == StoneColor.Black) Color.White else Color.DarkGray
                drawCircle(mark, radius = spacing * 0.12f, center = Offset(c.x, c.y), style = Stroke(width = spacing * 0.04f))
            }
        } else {
            val pad = min(size.width, size.height) * 0.08f
            val files = (rect.right - rect.left + 1)
            val ranks = (rect.top - rect.bottom + 1)
            val innerW = size.width - pad * 2
            val innerH = size.height - pad * 2
            val spacing = min(innerW / (files - 1).coerceAtLeast(1), innerH / (ranks - 1).coerceAtLeast(1))
            val origin = Offset(
                x = (size.width - spacing * (files - 1)) / 2f,
                y = (size.height - spacing * (ranks - 1)) / 2f,
            )
            val geom = BoardGeom(spacing, origin, rect)
            val grid = Rect(
                left = origin.x,
                top = origin.y,
                right = origin.x + spacing * (files - 1),
                bottom = origin.y + spacing * (ranks - 1),
            )
            drawWood(grid, spacing)
            drawWalls(grid, edges, spacing)
            drawGrid(geom, edges)
            drawStars(geom)
            drawCoordinates(geom, measurer)
            for (point in targets) {
                val c = geom.center(point)
                drawCircle(Color(0x66C62828), radius = spacing * 0.22f, center = c)
            }
            for ((point, color) in stones) {
                drawStone(geom.center(point), spacing * 0.46f, color)
            }
            if (lastMove != null && lastMove in stones) {
                val c = geom.center(lastMove)
                val mark = if (stones[lastMove] == StoneColor.Black) Color.White else Color.DarkGray
                drawCircle(mark, radius = spacing * 0.12f, center = c, style = Stroke(width = spacing * 0.04f))
            }
        }
    }
}

private fun hitOnBoard(
    canvasWidth: Float,
    canvasHeight: Float,
    rect: BoardRect,
    overlayImage: ImageBitmap?,
    imageGrid: ImageGrid?,
    x: Float,
    y: Float,
): Point? {
    if (overlayImage != null) {
        val overlay = overlayLayout(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            imageWidth = overlayImage.width.toFloat(),
            imageHeight = overlayImage.height.toFloat(),
            imageGrid = imageGrid ?: ImageGrid(0f, 0f, 1f, 1f),
            rect = rect,
        )
        return overlay.hit(x, y)
    }
    val pad = min(canvasWidth, canvasHeight) * 0.08f
    val files = (rect.right - rect.left + 1)
    val ranks = (rect.top - rect.bottom + 1)
    val spacing = min((canvasWidth - pad * 2) / (files - 1).coerceAtLeast(1), (canvasHeight - pad * 2) / (ranks - 1).coerceAtLeast(1))
    val origin = Offset(
        x = (canvasWidth - spacing * (files - 1)) / 2f,
        y = (canvasHeight - spacing * (ranks - 1)) / 2f,
    )
    return BoardGeom(spacing, origin, rect).hit(Offset(x, y))
}

private fun DrawScope.drawOverlayGrid(overlay: OverlayLayout, edges: Edges) {
    val stroke = min(overlay.spacingX, overlay.spacingY) * 0.035f
    val edgeStroke = min(overlay.spacingX, overlay.spacingY) * 0.07f
    val ink = Color(0xCC3A2712)
    for (file in overlay.rect.files) {
        val a = overlay.center(Point(file, overlay.rect.bottom))
        val b = overlay.center(Point(file, overlay.rect.top))
        drawLine(ink, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = stroke)
    }
    for (rank in overlay.rect.ranks) {
        val a = overlay.center(Point(overlay.rect.left, rank))
        val b = overlay.center(Point(overlay.rect.right, rank))
        drawLine(ink, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = stroke)
    }
    val tl = overlay.center(Point(overlay.rect.left, overlay.rect.top))
    val tr = overlay.center(Point(overlay.rect.right, overlay.rect.top))
    val bl = overlay.center(Point(overlay.rect.left, overlay.rect.bottom))
    val br = overlay.center(Point(overlay.rect.right, overlay.rect.bottom))
    if (edges.top == EdgeKind.Real) drawLine(ink, Offset(tl.x, tl.y), Offset(tr.x, tr.y), strokeWidth = edgeStroke)
    if (edges.bottom == EdgeKind.Real) drawLine(ink, Offset(bl.x, bl.y), Offset(br.x, br.y), strokeWidth = edgeStroke)
    if (edges.left == EdgeKind.Real) drawLine(ink, Offset(tl.x, tl.y), Offset(bl.x, bl.y), strokeWidth = edgeStroke)
    if (edges.right == EdgeKind.Real) drawLine(ink, Offset(tr.x, tr.y), Offset(br.x, br.y), strokeWidth = edgeStroke)
}

private fun DrawScope.drawOverlayCoordinates(overlay: OverlayLayout, measurer: TextMeasurer) {
    val spacing = min(overlay.spacingX, overlay.spacingY)
    val style = TextStyle(color = Color(0xFF3A2712), fontSize = (spacing * 0.28f).sp)
    for (file in overlay.rect.files) {
        val p = overlay.center(Point(file, overlay.rect.bottom))
        val label = Point.FILE_CHARS[file].toString()
        val layout = measurer.measure(label, style)
        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y + spacing * 0.28f))
    }
    for (rank in overlay.rect.ranks) {
        val p = overlay.center(Point(overlay.rect.right, rank))
        val label = rank.toString()
        val layout = measurer.measure(label, style)
        drawText(layout, topLeft = Offset(p.x + spacing * 0.28f, p.y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawWood(grid: Rect, spacing: Float) {
    val pad = spacing * 0.7f
    val area = Rect(grid.left - pad, grid.top - pad, grid.right + pad, grid.bottom + pad)
    drawRect(Color(0xFFD7B074), topLeft = area.topLeft, size = area.size)
    for (i in 0..18) {
        val y = area.top + area.height * (i / 18f)
        drawLine(
            color = Color(0x55B07A3A),
            start = Offset(area.left, y),
            end = Offset(area.right, y),
            strokeWidth = 1.2f,
        )
    }
}

private fun DrawScope.drawWalls(grid: Rect, edges: Edges, spacing: Float) {
    val band = spacing * 0.28f
    val wall = Color(0xFF4A3A28)
    if (edges.left == EdgeKind.Wall) {
        drawRect(wall.copy(alpha = 0.35f), topLeft = Offset(grid.left - band, grid.top - band), size = Size(band, grid.height + band * 2))
    }
    if (edges.right == EdgeKind.Wall) {
        drawRect(wall.copy(alpha = 0.35f), topLeft = Offset(grid.right, grid.top - band), size = Size(band, grid.height + band * 2))
    }
    if (edges.top == EdgeKind.Wall) {
        drawRect(wall.copy(alpha = 0.35f), topLeft = Offset(grid.left - band, grid.top - band), size = Size(grid.width + band * 2, band))
    }
    if (edges.bottom == EdgeKind.Wall) {
        drawRect(wall.copy(alpha = 0.35f), topLeft = Offset(grid.left - band, grid.bottom), size = Size(grid.width + band * 2, band))
    }
}

private fun DrawScope.drawGrid(geom: BoardGeom, edges: Edges) {
    val stroke = geom.spacing * 0.035f
    val edgeStroke = geom.spacing * 0.07f
    val ink = Color(0xFF3A2712)
    for (file in geom.rect.files) {
        val a = geom.center(Point(file, geom.rect.bottom))
        val b = geom.center(Point(file, geom.rect.top))
        drawLine(ink, a, b, strokeWidth = stroke)
    }
    for (rank in geom.rect.ranks) {
        val a = geom.center(Point(geom.rect.left, rank))
        val b = geom.center(Point(geom.rect.right, rank))
        drawLine(ink, a, b, strokeWidth = stroke)
    }
    val tl = geom.center(Point(geom.rect.left, geom.rect.top))
    val tr = geom.center(Point(geom.rect.right, geom.rect.top))
    val bl = geom.center(Point(geom.rect.left, geom.rect.bottom))
    val br = geom.center(Point(geom.rect.right, geom.rect.bottom))
    if (edges.top == EdgeKind.Real) drawLine(ink, tl, tr, strokeWidth = edgeStroke)
    if (edges.bottom == EdgeKind.Real) drawLine(ink, bl, br, strokeWidth = edgeStroke)
    if (edges.left == EdgeKind.Real) drawLine(ink, tl, bl, strokeWidth = edgeStroke)
    if (edges.right == EdgeKind.Real) drawLine(ink, tr, br, strokeWidth = edgeStroke)
}

private fun DrawScope.drawStars(geom: BoardGeom) {
    val stars = listOf(3, 9, 15).flatMap { f -> listOf(3, 9, 15).map { r -> Point(f, r + 1) } }
        .filter { geom.rect.contains(it) }
    for (p in stars) {
        drawCircle(Color(0xFF3A2712), radius = geom.spacing * 0.08f, center = geom.center(p))
    }
}

private fun DrawScope.drawCoordinates(geom: BoardGeom, measurer: TextMeasurer) {
    val style = TextStyle(color = Color(0xFF3A2712), fontSize = (geom.spacing * 0.28f).sp)
    for (file in geom.rect.files) {
        val p = geom.center(Point(file, geom.rect.bottom))
        val label = Point.FILE_CHARS[file].toString()
        val layout = measurer.measure(label, style)
        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y + geom.spacing * 0.28f))
    }
    for (rank in geom.rect.ranks) {
        val p = geom.center(Point(geom.rect.right, rank))
        val label = rank.toString()
        val layout = measurer.measure(label, style)
        drawText(layout, topLeft = Offset(p.x + geom.spacing * 0.28f, p.y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawStone(center: Offset, radius: Float, color: StoneColor) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.28f),
        radius = radius,
        center = center + Offset(radius * 0.12f, radius * 0.18f),
    )
    val brush = if (color == StoneColor.Black) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF5A5A5A), Color(0xFF1A1A1A), Color(0xFF050505)),
            center = center - Offset(radius * 0.32f, radius * 0.38f),
            radius = radius * 1.45f,
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFE8E8E8), Color(0xFFC4C4C4)),
            center = center - Offset(radius * 0.32f, radius * 0.38f),
            radius = radius * 1.45f,
        )
    }
    drawCircle(brush = brush, radius = radius, center = center)
    drawCircle(
        color = Color.White.copy(alpha = if (color == StoneColor.Black) 0.22f else 0.55f),
        radius = radius * 0.18f,
        center = center - Offset(radius * 0.28f, radius * 0.32f),
    )
}


