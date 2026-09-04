package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.Point
import kotlin.test.Test
import kotlin.test.assertTrue

class LdrzZoneTest {
    @Test
    fun seedZoneCoversLibertiesAndNearbyEmptiesNotWholeRegion() {
        val problem = (LdrzJson.parse(TINY_DEAD_JSON, "tiny_dead.json") as LdrzLoad.Ok).problem
        val pos = problem.toPosition()
        val seed = LdrzZone.seed(problem, pos)
        assertTrue(Point.parseOrThrow("A1") in seed, seed.toString())
        assertTrue(Point.parseOrThrow("B1") in seed, seed.toString())
        assertTrue(seed.size < problem.region.size || problem.region.size <= 12, "seed=$seed region=${problem.region.size}")
    }

    @Test
    fun twoEyeAliveZoneIncludesTheEyes() {
        val problem = twoEyeLiveProblem()
        val pos = problem.toPosition()
        val zone = LdrzZone.terminalAlive(pos, problem.defenderColor(), problem.defenderTargets())
        assertTrue(Point.parseOrThrow("B3") in zone, zone.toString())
        assertTrue(Point.parseOrThrow("C2") in zone, zone.toString())
        assertTrue(Point.parseOrThrow("A1") in zone, zone.toString())
    }

    @Test
    fun dilateAddsNeighbours() {
        val problem = (LdrzJson.parse(TINY_DEAD_JSON, "tiny_dead.json") as LdrzLoad.Ok).problem
        val pos = problem.toPosition()
        val grown = LdrzZone.dilate(setOf(Point.parseOrThrow("A1")), pos)
        assertTrue(Point.parseOrThrow("B1") in grown, grown.toString())
        assertTrue(Point.parseOrThrow("A2") in grown, grown.toString())
    }
}
