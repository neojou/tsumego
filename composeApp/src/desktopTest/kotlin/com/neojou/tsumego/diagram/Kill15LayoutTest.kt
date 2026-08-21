package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.pt
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Independent of the detector: 15K-kill.png is a P–T / 15–19 上右角.
 * Q19 black, T19 white, T16 black, P19 empty. Overlay centers must sit on those stones.
 */
class Kill15LayoutTest {
    private val bytes: ByteArray = loadPng("15K-kill.png")

    @Test
    fun overlayCentersSitOnThePrintedStones() {
        val layout = detectDiagramLayout(bytes)
        assertNotNull(layout)
        val image = Image.makeFromEncoded(bytes)
        val width = image.width.toFloat()
        val height = image.height.toFloat()
        val overlay = overlayLayout(
            canvasWidth = width,
            canvasHeight = height,
            imageWidth = width,
            imageHeight = height,
            imageGrid = layout.imageGrid,
            rect = layout.rect,
        )
        fun dump(label: String): String {
            val c = overlay.center(pt(label))
            return "$label@${c.x},${c.y}=${sampleColor(image, c)} rect=${layout.rect} grid=${layout.imageGrid}"
        }
        assertEquals(StoneColor.Black, sampleColor(image, overlay.center(pt("Q19"))), dump("Q19"))
        assertEquals(StoneColor.White, sampleColor(image, overlay.center(pt("T19"))), dump("T19"))
        assertEquals(StoneColor.Black, sampleColor(image, overlay.center(pt("T16"))), dump("T16"))
        assertEquals(StoneColor.Black, sampleColor(image, overlay.center(pt("Q16"))), dump("Q16"))
        assertNull(sampleColor(image, overlay.center(pt("P19"))), dump("P19"))
        assertNull(sampleColor(image, overlay.center(pt("S19"))), dump("S19"))
    }

    @Test
    fun photoStonesMatchThePrintedColors() {
        val layout = detectDiagramLayout(bytes)
        assertNotNull(layout)
        assertEquals(StoneColor.Black, layout.stones[pt("Q19")])
        assertEquals(StoneColor.White, layout.stones[pt("T19")])
        assertEquals(StoneColor.White, layout.stones[pt("R19")])
        assertEquals(StoneColor.Black, layout.stones[pt("T16")])
        assertEquals(StoneColor.Black, layout.stones[pt("Q16")])
        assertNull(layout.stones[pt("P19")])
        assertNull(layout.stones[pt("S19")])
        assertNull(layout.stones[pt("T15")])
    }

    @Test
    fun detectedCropIsThePrintedPT1519Corner() {
        val layout = detectDiagramLayout(bytes)
        assertNotNull(layout)
        val dump = "rect=${layout.rect} edges=${layout.edges} grid=${layout.imageGrid} " +
            "files=${layout.rect.right - layout.rect.left + 1} ranks=${layout.rect.top - layout.rect.bottom + 1} " +
            "stones=${layout.stones.keys.sorted().joinToString { it.label }}"
        assertEquals(Point.fileIndex('P'), layout.rect.left, dump)
        assertEquals(Point.fileIndex('T'), layout.rect.right, dump)
        assertEquals(15, layout.rect.bottom, dump)
        assertEquals(19, layout.rect.top, dump)
    }
}

internal fun sampleColor(image: Image, pos: ScreenPos): StoneColor? =
    sampleColor(image, pos.x, pos.y)

internal fun sampleColor(image: Image, x: Float, y: Float): StoneColor? {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(image.width, image.height, ColorAlphaType.UNPREMUL))
    check(image.readPixels(bitmap))
    val pix = checkNotNull(bitmap.peekPixels())
    val radius = (min(image.width, image.height) * 0.012f).toInt().coerceAtLeast(4)
    var n = 0
    var lumSum = 0.0
    var blueSum = 0.0
    val r2 = radius * radius
    val x0 = x.toInt()
    val y0 = y.toInt()
    for (dy in -radius..radius) {
        for (dx in -radius..radius) {
            if (dx * dx + dy * dy > r2) continue
            val px = (x0 + dx).coerceIn(0, image.width - 1)
            val py = (y0 + dy).coerceIn(0, image.height - 1)
            val c = pix.getColor(px, py)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lumSum += 0.299 * r + 0.587 * g + 0.114 * b
            blueSum += b
            n++
        }
    }
    if (n == 0) return null
    val lum = lumSum / n
    val blue = blueSum / n
    return when {
        lum < 95 -> StoneColor.Black
        lum > 200 && blue > 170 -> StoneColor.White
        else -> null
    }
}

internal fun loadPng(name: String): ByteArray {
    val candidates = listOf(
        File("../docs/tests/$name"),
        File("docs/tests/$name"),
        File("composeApp/../docs/tests/$name"),
    )
    val file = candidates.firstOrNull { it.exists() }
        ?: error("$name not found from ${File(".").absolutePath}")
    return file.readBytes()
}
