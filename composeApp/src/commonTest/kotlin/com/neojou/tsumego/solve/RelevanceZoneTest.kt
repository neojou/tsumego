package com.neojou.tsumego.solve

import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.classify.classify
import com.neojou.tsumego.openingWhiteLifeProblem
import com.neojou.tsumego.pt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelevanceZoneTest {
    @Test
    fun terminalZoneForTwoEyesIncludesEyesNotFarEmpties() {
        val problem = openingWhiteLifeProblem()
        val pos = requireNotNull(Position.initial(problem).play(pt("C5"), StoneColor.White))
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertEquals(Outcome.UnconditionalLive, outcome)
        val zone = terminalRelevanceZone(pos, problem, outcome)
        assertTrue(pt("B5") in zone, zone.toString())
        assertTrue(pt("D5") in zone, zone.toString())
        assertTrue(pt("C5") in zone, zone.toString())
        assertTrue(pt("A6") in zone, zone.toString())
        assertTrue(pt("A1") !in zone, zone.toString())
        assertTrue(pt("C1") !in zone, zone.toString())
    }

    @Test
    fun mustPlayDropsUnsearchedMovesOutsideTheZone() {
        val zone = setOf(pt("B5"), pt("C5"), pt("D5"))
        val a2 = Action.Move(pt("A2"))
        val c5 = Action.Move(pt("C5"))
        val d1 = Action.Move(pt("D1"))
        val kept = retainMustPlay(
            listed = listOf(a2, c5, d1),
            zone = zone,
            searched = setOf(a2),
        )
        assertEquals(listOf(c5), kept)
    }

    @Test
    fun zonePatternIgnoresStonesOutsideTheZone() {
        val problem = openingWhiteLifeProblem()
        val empty = Position.initial(problem)
        val withFar = requireNotNull(empty.play(pt("C1"), StoneColor.Black))
        val zone = setOf(pt("B5"), pt("C5"), pt("D5"), pt("A6"))
        val pattern = zonePattern(empty, StoneColor.White, 0, zone)
        assertTrue(zonePatternMatches(withFar, pattern))
        assertTrue(!zonePatternMatches(requireNotNull(empty.play(pt("C5"), StoneColor.White)), pattern))
    }

    @Test
    fun dilateAddsTheMoveAndNeighbouringLiberties() {
        val problem = openingWhiteLifeProblem()
        val pos = Position.initial(problem)
        val grown = dilate(setOf(pt("C5")), pos, Action.Move(pt("C5")))
        assertTrue(pt("C5") in grown)
        assertTrue(pt("B5") in grown)
        assertTrue(pt("C4") in grown)
        assertTrue(pt("C6") in grown)
        assertTrue(pt("D5") in grown)
        assertTrue(pt("C1") !in grown, grown.toString())
        assertTrue(pt("D1") !in grown, grown.toString())
        assertTrue(pt("A1") !in grown, grown.toString())
    }
}
