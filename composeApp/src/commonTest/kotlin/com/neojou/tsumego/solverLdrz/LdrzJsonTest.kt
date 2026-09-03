package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.board.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LdrzJsonTest {
    @Test
    fun prefersMaskedSgfAndStemFromFileName() {
        val loaded = LdrzJson.parse(TINY_DEAD_JSON, "chao_vol1_p088.json")
        val ok = assertIs<LdrzLoad.Ok>(loaded)
        val p = ok.problem
        assertEquals("chao_vol1_p088", p.stem)
        assertEquals("chao_vol1_p088.json", p.sourceJsonName)
        assertEquals(StoneColor.Black, p.turnColor)
        assertEquals(LdrzGoal.TOKILL, p.blackGoal)
        assertEquals(LdrzGoal.TOLIVE, p.whiteGoal)
        assertEquals("A1", p.whiteCrucial.single().label)
        assertEquals("A1", p.stones.entries.single { it.value == StoneColor.White }.key.label)
        assertTrue("A19" !in p.stones.keys.map { it.label })
        assertEquals("aa", p.answerFirstMove)
        assertTrue(p.region.any { it.label == "B1" })
    }

    @Test
    fun missingFieldsDoNotThrow() {
        val empty = LdrzJson.parse("{}", "x.json")
        assertIs<LdrzLoad.Err>(empty)
        val noSgf = LdrzJson.parse("""{"turn_color":"b","region":"as"}""", "x.json")
        assertIs<LdrzLoad.Err>(noSgf)
        val garbage = LdrzJson.parse("not-json", "x.json")
        assertIs<LdrzLoad.Err>(garbage)
    }

    @Test
    fun badCoordRejectsWholeProblem() {
        val badRegion = LdrzJson.parse(
            """{"masked_sgf_str":"(;SZ[19]AB[as])","region":"as,ZZ"}""",
            "x.json",
        )
        assertIs<LdrzLoad.Err>(badRegion)
        val badCrucial = LdrzJson.parse(
            """{"masked_sgf_str":"(;SZ[19]AW[as])","white_crucial_stone":"qqq"}""",
            "x.json",
        )
        assertIs<LdrzLoad.Err>(badCrucial)
    }

    @Test
    fun fallsBackToRawSgf() {
        val loaded = LdrzJson.parse(
            """{"rawsgf":"(;SZ[19]AB[as]AW[bs]PL[W])","turn_color":"w"}""",
            "raw_only.json",
        )
        val ok = assertIs<LdrzLoad.Ok>(loaded)
        assertEquals(StoneColor.White, ok.problem.turnColor)
        assertEquals("A1", ok.problem.stones.entries.single { it.value == StoneColor.Black }.key.label)
    }
}

internal const val TINY_DEAD_JSON = """
{
    "filename": "tiny_dead.sgf",
    "category": "TOKILL",
    "rawsgf": "(;SZ[19]AB[aa])",
    "masked_sgf_str": "(;FF[4]SZ[19]AB[ar][br]AW[as]PL[B])",
    "turn_color": "b",
    "winning_color": "b",
    "black_crucial_stone": "",
    "white_crucial_stone": "as",
    "black_search_goal": "TOKILL",
    "white_search_goal": "TOLIVE",
    "answer_firstmove": "aa",
    "region": "as,bs,cs,ar,br,cr,aq,bq,cq"
}
"""
