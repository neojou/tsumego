package com.neojou.tsumego.ui

import androidx.compose.ui.geometry.Offset
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.pt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardChromeTest {
    @Test
    fun targetListLabelListsSortedCoordinates() {
        assertEquals("目標: （未標）", targetListLabel(emptySet()))
        assertEquals("目標: B2, C2, T18", targetListLabel(setOf(pt("T18"), pt("C2"), pt("B2"))))
    }

    @Test
    fun targetFrameIsASquareAroundTheStone() {
        val spacing = 40f
        val center = Offset(100f, 80f)
        val frame = targetFrameRect(center, spacing)
        val half = spacing * 0.50f
        assertEquals(center.x - half, frame.left, 0.01f)
        assertEquals(center.y - half, frame.top, 0.01f)
        assertEquals(center.x + half, frame.right, 0.01f)
        assertEquals(center.y + half, frame.bottom, 0.01f)
        assertTrue(frame.width == frame.height)
        assertTrue(frame.width > spacing * 0.46f * 2f)
    }

    @Test
    fun onlyRealBoardEdgesUseAThickOuterLine() {
        val spacing = 100f
        assertTrue(realOuterStroke(spacing) > innerGridStroke(spacing) * 2f)
        assertTrue(drawsThickOuterLine(EdgeKind.Real))
        assertTrue(!drawsThickOuterLine(EdgeKind.Wall))
    }
}
