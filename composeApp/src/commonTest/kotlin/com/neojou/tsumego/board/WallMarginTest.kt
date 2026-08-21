package com.neojou.tsumego.board

import com.neojou.tsumego.SMALL_TRICK_JSON
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallMarginTest {
    @Test
    fun wallEdgeWithAStoneGetsTwoEmptyRanks() {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(SMALL_TRICK_JSON)).problem
        assertEquals(14, loaded.rect.bottom)
        val playable = loaded.withOpenWallMargin()
        assertEquals(12, playable.rect.bottom)
        assertEquals(19, playable.rect.top)
        assertEquals(EdgeKind.Wall, playable.edges.bottom)
        assertEquals(EdgeKind.Real, playable.edges.top)
        assertEquals(StoneColor.Black, playable.stones[pt("R14")])
        assertNull(playable.stones[pt("R13")])
        assertNull(playable.stones[pt("R12")])
        assertTrue(playable.rect.contains(pt("R12")))
        assertTrue(playable.rect.contains(pt("O12")))
        assertEquals(null, playable.validationError())
    }

    @Test
    fun realEdgesAreNotExtended() {
        val (rect, edges) = lowerLeftCorner(files = 3, ranks = 3)
        val problem = Problem(
            rect,
            edges,
            mapOf(pt("A1") to StoneColor.Black, pt("B1") to StoneColor.Black, pt("A2") to StoneColor.Black),
            Goal.Live,
            setOf(pt("A1"), pt("B1"), pt("A2")),
        )
        val playable = problem.withOpenWallMargin()
        assertEquals(1, playable.rect.bottom)
        assertEquals(0, playable.rect.left)
    }
}
