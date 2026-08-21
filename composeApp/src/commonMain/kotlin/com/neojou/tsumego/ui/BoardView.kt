package com.neojou.tsumego.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.neojou.tsumego.BoardAlbedo
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

data class BoardGeom(
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

    fun fileLabelBottom(): Float = center(Point(rect.left, rect.bottom)).y + spacing * 0.70f

    fun rankLabelRight(): Float = center(Point(rect.right, rect.top)).x + spacing * 0.70f
}

fun targetListLabel(targets: Set<Point>): String =
    if (targets.isEmpty()) "目標: （未標）"
    else "目標: " + targets.sorted().joinToString(", ") { it.label }

fun targetFrameRect(center: Offset, spacing: Float): Rect {
    val r = targetRingRadius(spacing)
    return Rect(center.x - r, center.y - r, center.x + r, center.y + r)
}

fun stoneRadius(spacing: Float): Float = spacing * 0.50f

fun targetRingRadius(spacing: Float): Float = spacing * 0.56f

fun lastMoveMarkRadius(spacing: Float): Float = spacing * 0.10f

fun stoneFlattenY(oblique: Boolean): Float = if (oblique) 0.84f else 1f

fun innerGridStroke(spacing: Float): Float = spacing * 0.035f

fun realOuterStroke(spacing: Float): Float = spacing * 0.14f

fun drawsThickOuterLine(kind: EdgeKind): Boolean = kind == EdgeKind.Real

fun tableColor(): Color = Color(0xFF3E3428)

fun wallCutColor(): Color = Color(0xFF1A1510)

fun gridRect(geom: BoardGeom): Rect {
    val spacing = geom.spacing
    val origin = geom.origin
    return Rect(
        left = origin.x,
        top = origin.y,
        right = origin.x + spacing * (geom.rect.right - geom.rect.left),
        bottom = origin.y + spacing * (geom.rect.top - geom.rect.bottom),
    )
}

fun woodEdgePad(kind: EdgeKind, spacing: Float): Float =
    if (kind == EdgeKind.Real) spacing * 0.55f else 0f

fun wallCutWidth(spacing: Float): Float = spacing * 0.14f

fun woodRect(geom: BoardGeom, edges: Edges): Rect {
    val grid = gridRect(geom)
    val s = geom.spacing
    return Rect(
        left = grid.left - woodEdgePad(edges.left, s),
        top = grid.top - woodEdgePad(edges.top, s),
        right = grid.right + woodEdgePad(edges.right, s),
        bottom = grid.bottom + woodEdgePad(edges.bottom, s),
    )
}

fun isOnWood(geom: BoardGeom, edges: Edges, x: Float, y: Float): Boolean =
    woodRect(geom, edges).contains(Offset(x, y))

fun boardLayout(canvasWidth: Float, canvasHeight: Float, rect: BoardRect): BoardGeom {
    val fileGaps = (rect.right - rect.left).coerceAtLeast(1)
    val rankGaps = (rect.top - rect.bottom).coerceAtLeast(1)
    val labelFrac = 0.70f
    val woodFrac = 0.55f
    val spacing = min(
        canvasWidth / (fileGaps + woodFrac + labelFrac),
        canvasHeight / (rankGaps + woodFrac + labelFrac),
    ).coerceAtLeast(1f)
    val gridW = spacing * fileGaps
    val gridH = spacing * rankGaps
    val leftPad = spacing * woodFrac
    val topPad = spacing * woodFrac
    val rightPad = spacing * labelFrac
    val bottomPad = spacing * labelFrac
    val origin = Offset(
        x = leftPad + (canvasWidth - leftPad - rightPad - gridW).coerceAtLeast(0f) / 2f,
        y = topPad + (canvasHeight - topPad - bottomPad - gridH).coerceAtLeast(0f) / 2f,
    )
    return BoardGeom(spacing, origin, rect)
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
    drop: Point? = null,
    dropLift: Float = 0f,
    retract: Map<Point, StoneColor> = emptyMap(),
    retractT: Float = 1f,
    albedo: BoardAlbedo? = null,
    onClick: (Point) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val hinoki = albedo?.wood
    val whiteAlbedo = albedo?.white
    val blackAlbedo = albedo?.black
    val onClickLatest = rememberUpdatedState(onClick)
    val overlayLatest = rememberUpdatedState(overlayImage)
    val gridLatest = rememberUpdatedState(imageGrid)
    val rectLatest = rememberUpdatedState(rect)
    val enabledLatest = rememberUpdatedState(enabled)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (!enabledLatest.value) return@detectTapGestures
                    hitOnBoard(
                        canvasWidth = size.width.toFloat(),
                        canvasHeight = size.height.toFloat(),
                        rect = rectLatest.value,
                        overlayImage = overlayLatest.value,
                        imageGrid = gridLatest.value,
                        x = offset.x,
                        y = offset.y,
                    )?.let { onClickLatest.value(it) }
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
            for ((point, color) in retract) {
                val c = overlay.center(point)
                drawClamStone(
                    Offset(c.x, c.y), spacing, color, oblique = false,
                    albedo = if (color == StoneColor.Black) blackAlbedo else whiteAlbedo,
                    lastMove = false, target = false, lift = 0f, alpha = 1f - retractT,
                )
            }
            for ((point, color) in stones) {
                val c = overlay.center(point)
                val lift = if (point == drop) dropLift else 0f
                drawClamStone(
                    Offset(c.x, c.y), spacing, color, oblique = false,
                    albedo = if (color == StoneColor.Black) blackAlbedo else whiteAlbedo,
                    lastMove = point == lastMove, target = point in targets, lift = lift, alpha = 1f,
                )
            }
        } else {
            val geom = boardLayout(size.width, size.height, rect)
            val spacing = geom.spacing
            val grid = gridRect(geom)
            val wood = woodRect(geom, edges)
            drawRect(tableColor(), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, size.height))
            if (hinoki != null) {
                clipRect(wood.left, wood.top, wood.right, wood.bottom) {
                    drawTiled(hinoki, wood)
                }
            } else {
                drawWood(wood, spacing)
            }
            drawWallCuts(grid, edges, spacing)
            drawWalls(grid, edges, spacing)
            drawGrid(geom, edges)
            drawStars(geom)
            drawCoordinates(geom, measurer)
            for ((point, color) in retract) {
                drawClamStone(
                    geom.center(point), spacing, color, oblique = true,
                    albedo = if (color == StoneColor.Black) blackAlbedo else whiteAlbedo,
                    lastMove = false, target = false, lift = 0f, alpha = 1f - retractT,
                )
            }
            for ((point, color) in stones) {
                val lift = if (point == drop) dropLift else 0f
                drawClamStone(
                    geom.center(point), spacing, color, oblique = true,
                    albedo = if (color == StoneColor.Black) blackAlbedo else whiteAlbedo,
                    lastMove = point == lastMove, target = point in targets, lift = lift, alpha = 1f,
                )
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
    return boardLayout(canvasWidth, canvasHeight, rect).hit(Offset(x, y))
}

private fun DrawScope.drawOverlayGrid(overlay: OverlayLayout, edges: Edges) {
    val spacing = min(overlay.spacingX, overlay.spacingY)
    val stroke = innerGridStroke(spacing)
    val edgeStroke = realOuterStroke(spacing)
    val ink = Color(0xCC3A2712)
    val cut = Color(0xAA8D6E4C)
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
        floatArrayOf(spacing * 0.18f, spacing * 0.14f),
        0f,
    )
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
    if (drawsThickOuterLine(edges.top)) drawLine(ink, Offset(tl.x, tl.y), Offset(tr.x, tr.y), strokeWidth = edgeStroke)
    if (drawsThickOuterLine(edges.bottom)) drawLine(ink, Offset(bl.x, bl.y), Offset(br.x, br.y), strokeWidth = edgeStroke)
    if (drawsThickOuterLine(edges.left)) drawLine(ink, Offset(tl.x, tl.y), Offset(bl.x, bl.y), strokeWidth = edgeStroke)
    if (drawsThickOuterLine(edges.right)) drawLine(ink, Offset(tr.x, tr.y), Offset(br.x, br.y), strokeWidth = edgeStroke)
    if (edges.top == EdgeKind.Wall) {
        drawLine(cut, Offset(tl.x, tl.y), Offset(tr.x, tr.y), strokeWidth = stroke, pathEffect = dash)
    }
    if (edges.bottom == EdgeKind.Wall) {
        drawLine(cut, Offset(bl.x, bl.y), Offset(br.x, br.y), strokeWidth = stroke, pathEffect = dash)
    }
    if (edges.left == EdgeKind.Wall) {
        drawLine(cut, Offset(tl.x, tl.y), Offset(bl.x, bl.y), strokeWidth = stroke, pathEffect = dash)
    }
    if (edges.right == EdgeKind.Wall) {
        drawLine(cut, Offset(tr.x, tr.y), Offset(br.x, br.y), strokeWidth = stroke, pathEffect = dash)
    }
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

private fun DrawScope.drawWood(area: Rect, spacing: Float) {
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

private fun DrawScope.drawWallCuts(grid: Rect, edges: Edges, spacing: Float) {
    val w = wallCutWidth(spacing)
    val ink = wallCutColor()
    if (edges.left == EdgeKind.Wall) {
        drawRect(
            ink,
            topLeft = Offset(grid.left - w, grid.top - w),
            size = androidx.compose.ui.geometry.Size(w, grid.height + w * 2f),
        )
    }
    if (edges.right == EdgeKind.Wall) {
        drawRect(
            ink,
            topLeft = Offset(grid.right, grid.top - w),
            size = androidx.compose.ui.geometry.Size(w, grid.height + w * 2f),
        )
    }
    if (edges.top == EdgeKind.Wall) {
        drawRect(
            ink,
            topLeft = Offset(grid.left - w, grid.top - w),
            size = androidx.compose.ui.geometry.Size(grid.width + w * 2f, w),
        )
    }
    if (edges.bottom == EdgeKind.Wall) {
        drawRect(
            ink,
            topLeft = Offset(grid.left - w, grid.bottom),
            size = androidx.compose.ui.geometry.Size(grid.width + w * 2f, w),
        )
    }
}

private fun DrawScope.drawWalls(grid: Rect, edges: Edges, spacing: Float) {
    val ink = Color(0xFF8D6E4C)
    val stroke = innerGridStroke(spacing)
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
        floatArrayOf(spacing * 0.18f, spacing * 0.14f),
        0f,
    )
    if (edges.left == EdgeKind.Wall) {
        drawLine(ink, Offset(grid.left, grid.top), Offset(grid.left, grid.bottom), strokeWidth = stroke, pathEffect = dash)
    }
    if (edges.right == EdgeKind.Wall) {
        drawLine(ink, Offset(grid.right, grid.top), Offset(grid.right, grid.bottom), strokeWidth = stroke, pathEffect = dash)
    }
    if (edges.top == EdgeKind.Wall) {
        drawLine(ink, Offset(grid.left, grid.top), Offset(grid.right, grid.top), strokeWidth = stroke, pathEffect = dash)
    }
    if (edges.bottom == EdgeKind.Wall) {
        drawLine(ink, Offset(grid.left, grid.bottom), Offset(grid.right, grid.bottom), strokeWidth = stroke, pathEffect = dash)
    }
}

private fun DrawScope.drawGrid(geom: BoardGeom, edges: Edges) {
    val stroke = innerGridStroke(geom.spacing)
    val edgeStroke = realOuterStroke(geom.spacing)
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
    if (drawsThickOuterLine(edges.top)) drawLine(ink, tl, tr, strokeWidth = edgeStroke)
    if (drawsThickOuterLine(edges.bottom)) drawLine(ink, bl, br, strokeWidth = edgeStroke)
    if (drawsThickOuterLine(edges.left)) drawLine(ink, tl, bl, strokeWidth = edgeStroke)
    if (drawsThickOuterLine(edges.right)) drawLine(ink, tr, br, strokeWidth = edgeStroke)
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

private fun DrawScope.drawTiled(image: ImageBitmap, area: Rect) {
    val tw = image.width.coerceAtLeast(1)
    val th = image.height.coerceAtLeast(1)
    var y = area.top
    while (y < area.bottom - 0.5f) {
        var x = area.left
        while (x < area.right - 0.5f) {
            val w = min(tw.toFloat(), area.right - x)
            val h = min(th.toFloat(), area.bottom - y)
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                dstOffset = IntOffset(x.toInt(), y.toInt()),
                dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
            )
            x += tw
        }
        y += th
    }
}

private fun DrawScope.drawClamStone(
    center: Offset,
    spacing: Float,
    color: StoneColor,
    oblique: Boolean,
    albedo: ImageBitmap?,
    lastMove: Boolean,
    target: Boolean,
    lift: Float,
    alpha: Float,
) {
    val rx = stoneRadius(spacing)
    val ry = rx * stoneFlattenY(oblique)
    val drawn = center + Offset(0f, -spacing * 0.35f * lift)
    val shadow = drawn + Offset(rx * 0.10f, ry * 0.22f)
    drawOval(
        color = Color.Black.copy(alpha = 0.28f * alpha),
        topLeft = Offset(shadow.x - rx, shadow.y - ry),
        size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
    )
    if (oblique) {
        drawOval(
            color = Color.Black.copy(alpha = 0.35f * alpha),
            topLeft = Offset(drawn.x - rx, drawn.y - ry + ry * 0.18f),
            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
        )
    }
    val oval = Path().apply {
        addOval(Rect(drawn.x - rx, drawn.y - ry, drawn.x + rx, drawn.y + ry))
    }
    if (albedo != null) {
        clipPath(oval) {
            drawImage(
                image = albedo,
                dstOffset = IntOffset((drawn.x - rx).toInt(), (drawn.y - ry).toInt()),
                dstSize = IntSize((rx * 2f).toInt().coerceAtLeast(1), (ry * 2f).toInt().coerceAtLeast(1)),
                alpha = alpha,
            )
        }
    } else {
        drawStone(drawn, rx, color)
    }
    val light = Color.White.copy(alpha = (if (color == StoneColor.Black) 0.16f else 0.28f) * alpha)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(light, Color.Transparent),
            center = drawn - Offset(rx * 0.32f, ry * 0.38f),
            radius = rx * 1.1f,
        ),
        topLeft = Offset(drawn.x - rx, drawn.y - ry),
        size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
    )
    if (target) {
        drawOval(
            color = Color(0xFFE53935).copy(alpha = alpha),
            topLeft = Offset(drawn.x - targetRingRadius(spacing), drawn.y - targetRingRadius(spacing) * stoneFlattenY(oblique)),
            size = androidx.compose.ui.geometry.Size(
                targetRingRadius(spacing) * 2f,
                targetRingRadius(spacing) * 2f * stoneFlattenY(oblique),
            ),
            style = Stroke(width = spacing * 0.06f),
        )
    }
    if (lastMove) {
        val mark = if (color == StoneColor.Black) Color.White else Color(0xFF3A2712)
        drawOval(
            color = mark.copy(alpha = alpha),
            topLeft = Offset(drawn.x - lastMoveMarkRadius(spacing), drawn.y - lastMoveMarkRadius(spacing) * stoneFlattenY(oblique)),
            size = androidx.compose.ui.geometry.Size(
                lastMoveMarkRadius(spacing) * 2f,
                lastMoveMarkRadius(spacing) * 2f * stoneFlattenY(oblique),
            ),
        )
    }
}

private fun DrawScope.drawStone(center: Offset, radius: Float, color: StoneColor) {
    val brush = if (color == StoneColor.Black) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF5A5A5A), Color(0xFF1A1A1A), Color(0xFF050505)),
            center = center - Offset(radius * 0.32f, radius * 0.38f),
            radius = radius * 1.45f,
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFF6EFE2), Color(0xFFE8D9C4), Color(0xFFC4B49A)),
            center = center - Offset(radius * 0.32f, radius * 0.38f),
            radius = radius * 1.45f,
        )
    }
    drawCircle(brush = brush, radius = radius, center = center)
}


