package com.neojou.tsumego.play

import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.pt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoneFxTest {
    @Test
    fun legalPlaceOnlyMakesPlaceSoundAndDrop() {
        val before = mapOf(pt("B2") to StoneColor.White)
        val after = before + (pt("C3") to StoneColor.Black)
        val fx = stoneFx(before, after, lastMove = pt("C3"), lastMoveIsPass = false, undo = false)
        assertEquals(listOf(StoneSoundKind.Place), fx.sounds)
        assertEquals(pt("C3"), fx.drop)
        assertTrue(fx.retract.isEmpty())
    }

    @Test
    fun capturingPlaceMakesPlaceAndCapture() {
        val before = mapOf(
            pt("B2") to StoneColor.White,
            pt("A2") to StoneColor.Black,
            pt("B1") to StoneColor.Black,
            pt("C2") to StoneColor.Black,
        )
        val after = mapOf(
            pt("A2") to StoneColor.Black,
            pt("B1") to StoneColor.Black,
            pt("C2") to StoneColor.Black,
            pt("B3") to StoneColor.Black,
        )
        val fx = stoneFx(before, after, lastMove = pt("B3"), lastMoveIsPass = false, undo = false)
        assertEquals(listOf(StoneSoundKind.Place, StoneSoundKind.Capture), fx.sounds)
        assertEquals(pt("B3"), fx.drop)
        assertEquals(mapOf(pt("B2") to StoneColor.White), fx.retract)
    }

    @Test
    fun passAndUndoAreSilent() {
        val stones = mapOf(pt("B2") to StoneColor.White)
        assertEquals(
            StoneFx.None,
            stoneFx(stones, stones, lastMove = null, lastMoveIsPass = true, undo = false),
        )
        assertEquals(
            StoneFx.None,
            stoneFx(stones, emptyMap(), lastMove = pt("B2"), lastMoveIsPass = false, undo = true),
        )
    }
}
