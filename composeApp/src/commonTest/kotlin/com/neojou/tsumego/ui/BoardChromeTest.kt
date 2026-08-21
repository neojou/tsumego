package com.neojou.tsumego.ui

import androidx.compose.ui.geometry.Offset
import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
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
    fun targetRingSitsOutsideTheStone() {
        val spacing = 40f
        val stone = stoneRadius(spacing)
        val ring = targetRingRadius(spacing)
        assertTrue(ring > stone)
        assertTrue(ring < spacing * 0.70f)
        val mark = lastMoveMarkRadius(spacing)
        assertTrue(mark < stone * 0.45f)
    }

    @Test
    fun onlyRealBoardEdgesUseAThickOuterLine() {
        val spacing = 100f
        assertTrue(realOuterStroke(spacing) > innerGridStroke(spacing) * 2f)
        assertTrue(drawsThickOuterLine(EdgeKind.Real))
        assertTrue(!drawsThickOuterLine(EdgeKind.Wall))
    }

    @Test
    fun woodStopsAtAWallAndExtendsPastARealEdge() {
        val rect = BoardRect(left = 15, right = 18, bottom = 15, top = 19)
        val geom = boardLayout(canvasWidth = 800f, canvasHeight = 800f, rect = rect)
        val edges = Edges(
            left = EdgeKind.Wall,
            right = EdgeKind.Real,
            bottom = EdgeKind.Wall,
            top = EdgeKind.Real,
        )
        val grid = gridRect(geom)
        val wood = woodRect(geom, edges)
        assertEquals(grid.left, wood.left, 0.01f)
        assertEquals(grid.bottom, wood.bottom, 0.01f)
        assertTrue(wood.right > grid.right + geom.spacing * 0.4f)
        assertTrue(wood.top < grid.top - geom.spacing * 0.4f)
        assertTrue(!isOnWood(geom, edges, grid.left - 8f, (grid.top + grid.bottom) / 2f))
        assertTrue(isOnWood(geom, edges, (grid.left + grid.right) / 2f, (grid.top + grid.bottom) / 2f))
    }
}
