package com.neojou.tsumego.play

import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.formatSearchPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecisionTreeTest {
    @Test
    fun firstSeenWhiteOrderIsKeptWhenALaterWhiteIsSmaller() {
        val tree = DecisionTreeProjection()
        tree.notePath()
        tree.show(white("A2"), black("B2"), "白下 C2 -> 黑勝", replace = false)
        tree.notePath()
        tree.show(white("A1"), black("B1"), "白下 C1 -> 白勝", replace = false)
        val view = tree.view()
        assertEquals(2, view.pathCount)
        assertEquals(2, view.leafCount)
        assertEquals("白下 A2", view.lines[0].text)
        assertEquals(0, view.lines[0].indent)
        assertEquals("白下 A1", view.lines.first { it.indent == 0 && it.text.startsWith("白下 A1") }.text)
        assertTrue(view.lines.indexOfFirst { it.text == "白下 A2" } < view.lines.indexOfFirst { it.text == "白下 A1" })
        assertTrue(view.lines.none { it.text.startsWith("1.") })
    }

    @Test
    fun newBlackHangsLastUnderTheSameWhite() {
        val tree = DecisionTreeProjection()
        tree.notePath()
        tree.show(white("A1"), black("B12"), "白下 C2 -> 黑勝", replace = false)
        tree.notePath()
        tree.show(white("A1"), black("B11"), "白下 C1 -> 白勝", replace = false)
        val blacks = tree.view().lines.filter { it.indent == 1 }.map { it.text }
        assertEquals(listOf("黑下 B12", "黑下 B11"), blacks)
    }

    @Test
    fun replaceRewritesTheSameLeafWithoutMovingIt() {
        val tree = DecisionTreeProjection()
        tree.notePath()
        tree.show(white("A1"), black("B11"), "白下 C1 -> 白勝", replace = false)
        tree.notePath()
        tree.show(white("A1"), black("B12"), "白下 C2 -> 黑勝", replace = false)
        tree.show(white("A1"), black("B11"), "白下 C3 -> 黑下 D3 -> 黑勝", replace = true)
        val view = tree.view()
        assertEquals(2, view.leafCount)
        val b11 = view.lines.indexOfFirst { it.text == "黑下 B11" }
        assertEquals("白下 C3 -> 黑下 D3 -> 黑勝", view.lines[b11 + 1].text)
        assertEquals(2, view.lines[b11 + 1].indent)
        assertEquals("黑下 B12", view.lines[b11 + 2].text)
    }

    @Test
    fun whiteImmediateTerminalHasNoFakeBlack() {
        val tree = DecisionTreeProjection()
        tree.notePath()
        tree.show(white("B3"), black = null, continuation = "白勝", replace = false)
        val view = tree.view()
        assertEquals(listOf("白下 B3", "白勝"), view.lines.map { it.text })
        assertEquals(listOf(0, 1), view.lines.map { it.indent })
    }

    @Test
    fun leafCapStopsNewLeavesButPathCountStillGrows() {
        val tree = DecisionTreeProjection(leafLimit = 2)
        repeat(5) { i ->
            tree.notePath()
            tree.show(white("A1"), black("B${i + 1}"), "白勝", replace = false)
        }
        val view = tree.view()
        assertEquals(5, view.pathCount)
        assertEquals(2, view.leafCount)
    }

    @Test
    fun formatSearchPathEndsWithBlackOrWhiteWin() {
        assertEquals(
            "白下 B3 -> 黑下 A3 -> 黑勝",
            formatSearchPath(listOf(white("B3"), black("A3")), blackForces = true),
        )
        assertEquals(
            "白下 B3 -> 白勝",
            formatSearchPath(listOf(white("B3")), blackForces = false),
        )
        assertEquals("黑勝", formatSearchPath(emptyList(), blackForces = true))
    }
}

private fun white(label: String) = Action.Move(pt(label))
private fun black(label: String) = Action.Move(pt(label))
