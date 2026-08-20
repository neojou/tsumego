package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.pt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlayLayoutTest {
    /**
     * Cropped 6×6 upper-right diagram (O–T, 14–19) drawn in a 1000×1000 canvas
     * from a 1000×1000 image. The lattice occupies 10%–80% x and 20%–90% y,
     * matching a photo that still has file labels above and rank labels to the right.
     */
    private val rect = BoardRect(left = 13, right = 18, bottom = 14, top = 19)
    private val grid = ImageGrid(left = 0.10f, top = 0.20f, right = 0.80f, bottom = 0.90f)
    private val layout = overlayLayout(
        canvasWidth = 1000f,
        canvasHeight = 1000f,
        imageWidth = 1000f,
        imageHeight = 1000f,
        imageGrid = grid,
        rect = rect,
    )

    @Test
    fun topRightIntersectionIsT19OnThePhotoGridNotTheImageCorner() {
        val t19 = layout.center(pt("T19"))
        assertEquals(800f, t19.x, 0.5f)
        assertEquals(200f, t19.y, 0.5f)
        assertEquals(pt("T19"), layout.hit(t19.x, t19.y))
    }

    @Test
    fun bottomLeftIntersectionIsO14() {
        val o14 = layout.center(pt("O14"))
        assertEquals(100f, o14.x, 0.5f)
        assertEquals(900f, o14.y, 0.5f)
        assertEquals(pt("O14"), layout.hit(o14.x, o14.y))
    }

    @Test
    fun q16IsTheHoshiInsideTheCrop() {
        val q16 = layout.center(pt("Q16"))
        assertEquals(100f + 2 * 140f, q16.x, 0.5f)
        assertEquals(200f + 3 * 140f, q16.y, 0.5f)
        assertEquals(pt("Q16"), layout.hit(q16.x, q16.y))
    }

    @Test
    fun clickOnFileLabelsAboveTheGridDoesNotHitAPoint() {
        assertNull(layout.hit(800f, 50f))
        assertNull(layout.hit(100f, 50f))
    }

    @Test
    fun stretchingTheWholeImageWouldMissT19() {
        val stretched = overlayLayout(
            canvasWidth = 1000f,
            canvasHeight = 1000f,
            imageWidth = 1000f,
            imageHeight = 1000f,
            imageGrid = ImageGrid(left = 0f, top = 0f, right = 1f, bottom = 1f),
            rect = rect,
        )
        val photoT19 = layout.center(pt("T19"))
        val stretchedHit = stretched.hit(photoT19.x, photoT19.y)
        assertTrue(stretchedHit == null || stretchedHit != pt("T19"))
        assertTrue(abs(stretched.center(pt("T19")).x - photoT19.x) > 20f)
    }
}
