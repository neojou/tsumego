package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LdrzCoordTest {
    @Test
    fun cornersMapA1LowerLeftAndSkipI() {
        assertEquals("A19", LdrzCoord.toPoint("aa")!!.label)
        assertEquals("A1", LdrzCoord.toPoint("as")!!.label)
        assertEquals("T19", LdrzCoord.toPoint("sa")!!.label)
        assertEquals("T1", LdrzCoord.toPoint("ss")!!.label)
        assertEquals("H19", LdrzCoord.toPoint("ha")!!.label)
        assertEquals("J19", LdrzCoord.toPoint("ia")!!.label)
        assertEquals("K19", LdrzCoord.toPoint("ja")!!.label)
        assertEquals("Q2", LdrzCoord.toPoint("pr")!!.label)
        assertEquals("S2", LdrzCoord.toPoint("rr")!!.label)
    }

    @Test
    fun pointToSgfRoundTripsAndSkipI() {
        assertEquals("aa", LdrzCoord.toSgf(Point.parseOrThrow("A19")))
        assertEquals("as", LdrzCoord.toSgf(Point.parseOrThrow("A1")))
        assertEquals("ia", LdrzCoord.toSgf(Point.parseOrThrow("J19")))
        assertEquals("ha", LdrzCoord.toSgf(Point.parseOrThrow("H19")))
        assertEquals("or", LdrzCoord.toSgf(Point.parseOrThrow("P2")))
        assertEquals("pr", LdrzCoord.toSgf(Point.parseOrThrow("Q2")))
        assertEquals("rr", LdrzCoord.toSgf(Point.parseOrThrow("S2")))
        val j = LdrzCoord.toPoint("ia")!!
        assertEquals('J', j.fileChar)
        assertEquals("ia", LdrzCoord.toSgf(j))
    }

    @Test
    fun rejectsOutOfBoardAndWrongLength() {
        assertNull(LdrzCoord.toPoint("at"))
        assertNull(LdrzCoord.toPoint("tt"))
        assertNull(LdrzCoord.toPoint("a"))
        assertNull(LdrzCoord.toPoint("abc"))
        assertNull(LdrzCoord.toPoint(""))
        assertNull(Point.parse("I1"))
        assertNull(LdrzCoord.parseList("as,ZZ"))
    }

    @Test
    fun parseListSplitsCommaRegion() {
        val pts = LdrzCoord.parseList("as,bs,pr")!!
        assertEquals(listOf("A1", "B1", "Q2"), pts.map { it.label })
        assertEquals(emptyList(), LdrzCoord.parseList(""))
        assertEquals(emptyList(), LdrzCoord.parseList("   "))
    }
}
