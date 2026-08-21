package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.pt
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Independent of the detector: 8K-kill.png is O–T / 11–19. S19 black, P17 white, T19 empty. */
class Kill8LayoutTest {
    private val bytes: ByteArray = loadPng("8K-kill.png")

    @Test
    fun detectedCropIsThePrintedOT1119Corner() {
        val layout = detectDiagramLayout(bytes)
        assertNotNull(layout)
        assertEquals(Point.fileIndex('O'), layout.rect.left)
        assertEquals(Point.fileIndex('T'), layout.rect.right)
        assertEquals(11, layout.rect.bottom)
        assertEquals(19, layout.rect.top)
    }

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
        assertEquals(StoneColor.Black, sampleColor(image, overlay.center(pt("S19"))))
        assertEquals(StoneColor.White, sampleColor(image, overlay.center(pt("P17"))))
        assertEquals(StoneColor.White, sampleColor(image, overlay.center(pt("Q12"))))
        assertEquals(StoneColor.Black, sampleColor(image, overlay.center(pt("R16"))))
        assertNull(sampleColor(image, overlay.center(pt("T19"))))
        assertNull(sampleColor(image, overlay.center(pt("O19"))))
    }
}
