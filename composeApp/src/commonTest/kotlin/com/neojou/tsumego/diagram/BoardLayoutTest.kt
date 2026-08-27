package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.ui.boardLayout
import com.neojou.tsumego.ui.coordinateFontSize
import com.neojou.tsumego.ui.coordinatePaintPx
import com.neojou.tsumego.ui.fileLabelOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardLayoutTest {
    @Test
    fun fileLettersFitInsideAShortCanvas() {
        val rect = BoardRect(left = 13, right = 18, bottom = 14, top = 19)
        val height = 360f
        val layout = boardLayout(canvasWidth = 800f, canvasHeight = height, rect = rect)
        val last = layout.center(Point(rect.left, rect.bottom))
        val drawBottom = last.y + fileLabelOffset(layout.spacing) + coordinateFontSize(layout.spacing)
        assertTrue(
            drawBottom <= height,
            "file letters clipped: bottom=$drawBottom canvas=$height spacing=${layout.spacing} lastY=${last.y}",
        )
        assertTrue(layout.fileLabelBottom() <= height)
    }

    @Test
    fun fileLettersAreNotCutInHalfAtDensityTwo() {
        val density = 2f
        val height = 360f
        val rect = BoardRect(left = 13, right = 18, bottom = 14, top = 19)
        val layout = boardLayout(canvasWidth = 800f, canvasHeight = height, rect = rect)
        val last = layout.center(Point(rect.left, rect.bottom))
        val glyph = coordinatePaintPx(layout.spacing, density)
        val bottom = last.y + fileLabelOffset(layout.spacing) + glyph
        assertEquals(
            coordinateFontSize(layout.spacing),
            glyph,
            0.5f,
        )
        assertTrue(
            bottom <= height,
            "file letters cut in half at density=$density: bottom=$bottom canvas=$height glyph=$glyph",
        )
    }

    @Test
    fun rankLabelsFitInsideANarrowCanvas() {
        val rect = BoardRect(left = 13, right = 18, bottom = 14, top = 19)
        val width = 360f
        val layout = boardLayout(canvasWidth = width, canvasHeight = 800f, rect = rect)
        val labelRight = layout.rankLabelRight()
        assertTrue(
            labelRight <= width,
            "rank label clipped: right=$labelRight canvas=$width spacing=${layout.spacing}",
        )
    }
}
