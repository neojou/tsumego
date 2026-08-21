package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.ui.boardLayout
import kotlin.test.Test
import kotlin.test.assertTrue

class BoardLayoutTest {
    @Test
    fun fileLabelsFitInsideAShortCanvas() {
        val rect = BoardRect(left = 13, right = 18, bottom = 14, top = 19)
        val height = 360f
        val layout = boardLayout(canvasWidth = 800f, canvasHeight = height, rect = rect)
        val labelBottom = layout.fileLabelBottom()
        assertTrue(
            labelBottom <= height,
            "file label clipped: bottom=$labelBottom canvas=$height spacing=${layout.spacing}",
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
