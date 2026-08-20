package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LatticeTest {
    @Test
    fun completeLatticeFillsASkippedFileCoveredByStones() {
        // Vertical lines measured on small_trick.png: Q is hidden under three white stones,
        // and a leftover peak sits past T on the label margin.
        val peaks = listOf(79f, 211f, 341f, 598f, 729f, 789f)
        val lines = completeLattice(peaks)
        assertEquals(6, lines.size)
        val gaps = lines.zipWithNext { a, b -> b - a }
        gaps.forEach { gap ->
            assertEquals(130.0, gap.toDouble(), 8.0)
        }
        assertEquals(79f, lines.first(), 1f)
        assertEquals(729f, lines.last(), 8f)
        assertTrue(lines.none { it > 760f })
    }

    @Test
    fun woodFrameLineAboveTheGridIsDropped() {
        val xs = listOf(79f, 211f, 341f, 472f, 598f, 729f)
        val ys = listOf(64f, 192f, 322f, 452f, 582f, 712f, 844f)
        val (chosenX, chosenY) = chooseLattices(
            xs = xs,
            ys = ys,
            imageWidth = 908,
            imageHeight = 946,
            crossing = { _, y -> if (y <= 70f) 1f else 80f },
        )
        assertEquals(xs, chosenX)
        assertEquals(ys.drop(1), chosenY)
    }

    @Test
    fun realEdgeFileWithWeakCrossingsIsKept() {
        val xs = listOf(79f, 211f, 341f, 472f, 598f, 729f)
        val ys = listOf(192f, 322f, 452f, 582f, 712f, 844f)
        val (chosenX, _) = chooseLattices(
            xs = xs,
            ys = ys,
            imageWidth = 908,
            imageHeight = 946,
            crossing = { x, _ -> if (x >= 700f) 0.2f else 80f },
        )
        assertEquals(xs, chosenX)
    }

    @Test
    fun labeledTopRightMarginsAreTheT19Corner() {
        val (rect, edges) = assignCrop(
            fileCount = 6,
            rankCount = 6,
            marginLeft = 0.6f,
            marginRight = 1.0f,
            marginTop = 1.0f,
            marginBottom = 0.7f,
        )
        assertEquals(Point.fileIndex('O'), rect.left)
        assertEquals(Point.fileIndex('T'), rect.right)
        assertEquals(14, rect.bottom)
        assertEquals(19, rect.top)
        assertEquals(EdgeKind.Wall, edges.left)
        assertEquals(EdgeKind.Real, edges.right)
        assertEquals(EdgeKind.Wall, edges.bottom)
        assertEquals(EdgeKind.Real, edges.top)
    }
}
