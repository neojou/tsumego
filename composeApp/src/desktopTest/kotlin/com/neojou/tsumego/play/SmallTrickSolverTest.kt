package com.neojou.tsumego.play

import com.neojou.tsumego.SMALL_TRICK_JSON
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SmallTrickSolverTest {
    @Test
    fun firstMoveRecordsSearchPaths() = runTest {
        val problem = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(SMALL_TRICK_JSON)).problem
        val session = testSession(problem, solver = AlphaBetaSolver())
        assertTrue(session.tryMove(pt("S19")))
        session.waitForIdle()
        val snap = session.state.value
        assertTrue(snap.status != PlayStatus.Timeout)
        assertTrue(snap.searchPaths.isNotEmpty(), "expected 搜尋路徑, status=${snap.status}")
    }
}
