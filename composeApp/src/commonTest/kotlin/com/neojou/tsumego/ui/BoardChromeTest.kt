package com.neojou.tsumego.ui

import androidx.compose.ui.graphics.Color
import com.neojou.tsumego.board.BoardRect
import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Edges
import com.neojou.tsumego.pt
import kotlin.math.pow
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

    @Test
    fun coordinateInkContrastsWithTheTable() {
        val ratio = contrastRatio(coordinateInk(), tableColor())
        assertTrue(
            ratio >= 4.5,
            "盤桌與座標幾乎同色: contrast=$ratio table=${tableColor()} ink=${coordinateInk()}",
        )
    }

    @Test
    fun playBoardOmitsTargetRings() {
        val targets = setOf(pt("T18"), pt("S19"), pt("R17"))
        assertEquals(emptySet(), playTargetMarks(targets))
        assertEquals(targets, confirmTargetMarks(targets))
    }

    @Test
    fun playStonesUseAShallowOverheadTilt() {
        // 12–18° from overhead → flattenY = cos(tilt) ∈ [cos18, cos12]
        val flatten = stoneFlattenY(oblique = true)
        assertTrue(
            flatten in 0.951f..0.979f,
            "flattenY=$flatten is not a 12–18° 俯視 (0.84 is ~33° pancake)",
        )
        assertEquals(1f, stoneFlattenY(oblique = false))
        val rim = stoneRimRatio(oblique = true)
        assertTrue(rim in 0.10f..0.22f, "rim=$rim should read as clam thickness, not a second disc")
    }
}

private fun contrastRatio(a: Color, b: Color): Double {
    val l1 = relativeLuminance(a)
    val l2 = relativeLuminance(b)
    val light = maxOf(l1, l2)
    val dark = minOf(l1, l2)
    return (light + 0.05) / (dark + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun lin(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(color.red) + 0.7152 * lin(color.green) + 0.0722 * lin(color.blue)
}
