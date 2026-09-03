package com.neojou.tsumego.solverLdrz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LdrzOutputTest {
    @Test
    fun stemAndFileNamesMatchOriginalReadme() {
        assertEquals("chao_vol1_p088", stemFromFileName("chao_vol1_p088.json"))
        assertEquals("chao_vol1_p088", stemFromFileName("/abs/path/chao_vol1_p088.json"))
        assertEquals("result/result_chao_vol1_p088.json", LdrzOutput.resultJsonPath("chao_vol1_p088"))
        assertEquals("result/uct_tree_chao_vol1_p088.sgf", LdrzOutput.uctTreePath("chao_vol1_p088"))
    }

    @Test
    fun resultJsonHasRequiredKeys() {
        val problem = (LdrzJson.parse(TINY_DEAD_JSON, "chao_vol1_p088.json") as LdrzLoad.Ok).problem
        val result = LdrzResult(
            status = LdrzStatus.DEAD,
            firstMoveSgf = "bs",
            numSimulations = 12,
            timeSeconds = 0.05,
            zoneCount = 9,
            principalLine = listOf(LdrzCoord.toPoint("bs")!!),
        )
        val text = LdrzOutput.resultJson(problem, result)
        assertTrue("\"engine\"" in text)
        assertTrue("kotlin-v1" in text)
        assertTrue("\"NumSimulations\"" in text)
        assertTrue("\"Time\"" in text)
        assertTrue("\"status\"" in text)
        assertTrue("DEAD" in text)
        assertTrue("\"first_move_sgf\"" in text)
        assertTrue("\"answer_firstmove\"" in text)
        assertTrue("\"zone_count\"" in text)
        assertTrue("\"goal_black\"" in text)
        assertTrue("\"goal_white\"" in text)
        assertTrue("\"turn_color\"" in text)
        assertTrue("\"problem\"" in text)
        assertTrue("chao_vol1_p088" in text)
        val parsed = LdrzOutput.parseResultJson(text)!!
        assertEquals(12, parsed.NumSimulations)
        assertEquals("DEAD", parsed.status)
        assertEquals("bs", parsed.first_move_sgf)
        assertEquals("aa", parsed.answer_firstmove)
    }

    @Test
    fun uctTreeSgfIsFf4WithSizeAndFirstMove() {
        val problem = (LdrzJson.parse(TINY_DEAD_JSON, "tiny_dead.json") as LdrzLoad.Ok).problem
        val result = LdrzResult(
            status = LdrzStatus.DEAD,
            firstMoveSgf = "bs",
            principalLine = listOf(LdrzCoord.toPoint("bs")!!),
            numSimulations = 3,
        )
        val sgf = LdrzOutput.uctTreeSgf(problem, result)
        assertTrue("FF[4]" in sgf, sgf)
        assertTrue("SZ[19]" in sgf, sgf)
        assertTrue("AB[" in sgf, sgf)
        assertTrue("AW[" in sgf, sgf)
        assertTrue("PL[B]" in sgf, sgf)
        assertTrue(";B[bs]" in sgf, sgf)
        assertTrue("RZONE:" in sgf, sgf)
        assertTrue("WIN:bs" in sgf, sgf)
        assertEquals("B[bs]", LdrzOutput.principalLineFromSgf(sgf))
    }

    @Test
    fun menuTitleAndOrder() {
        val item = ldrzMenuBarItem(enabled = true, onOpen = {}, onCalculate = {}, onShow = {})
        assertEquals("study-LD-RZ", item.label)
        assertEquals(listOf("Open", "Calculate", "Show"), item.children.map { it.label })
    }
}
