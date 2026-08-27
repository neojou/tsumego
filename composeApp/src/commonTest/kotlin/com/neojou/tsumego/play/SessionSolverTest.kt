package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Point
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.library.Samples
import com.neojou.tsumego.openingWhiteLifeProblem
import com.neojou.tsumego.pt
import com.neojou.tsumego.ImmediateTimeoutSolver
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.solve.AlphaBetaSolver
import com.neojou.tsumego.solve.Solver
import com.neojou.tsumego.solve.SolverInput
import com.neojou.tsumego.solve.SolverResult
import com.neojou.tsumego.solve.actions
import com.neojou.tsumego.solve.findOpeningWhiteLife
import com.neojou.tsumego.solve.rankMovesByPlayout
import com.neojou.tsumego.testSession
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionSolverTest {
    private val solver = AlphaBetaSolver(maxDepth = 12)

    @Test
    fun whiteRepliesWithLongestResistanceAndStableCoordinates() = runTest {
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        session.waitForIdle()
        val snap = session.state.value
        assertTrue(snap.status == PlayStatus.InProgress || snap.status == PlayStatus.Failure)
        assertEquals(pt("C5"), snap.lastMove)
        assertEquals(StoneColor.White, snap.stones[pt("C5")])
    }

    @Test
    fun searchPathsListWhiteC5WhenBlackTakesAnEye() = runTest {
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        session.waitForIdle()
        assertTrue(
            session.state.value.searchPaths.any { "白下 C5" in it },
            session.state.value.searchPaths.joinToString("\n"),
        )
    }

    @Test
    fun searchPathsDoNotListPass() = runTest {
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        session.waitForIdle()
        val snap = session.state.value
        val paths = snap.searchPaths
        assertTrue(paths.isNotEmpty(), "expected 搜尋路徑")
        assertTrue(
            paths.none { "停" in it },
            paths.joinToString("\n"),
        )
        assertTrue(snap.decisionTree.lines.none { "停" in it.text }, snap.decisionTree.lines.joinToString { it.text })
    }

    @Test
    fun atariKillSucceedsWithoutCaptureWhenWhiteCannotMakeEyes() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
            solver = solver,
        )
        assertTrue(session.tryMove(pt("C3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun failureShowsRefutationThenFailure() = runTest {
        val problem = cornerProblem(
            files = 3,
            ranks = 3,
            black = "B2",
            white = "A2,B1,C2",
            goal = Goal.Live,
            targets = "B2",
        )
        val session = testSession(problem, solver = solver)
        assertTrue(session.tryMove(pt("C3")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(PlayStatus.Failure, snap.status)
        assertTrue(snap.lastMove != null || snap.lastMoveIsPass)
        if (snap.lastMove == pt("B3")) {
            assertNull(snap.stones[pt("B2")])
        }
    }

    @Test
    fun koLiveProblemSucceedsOnUnconditionalLive() = runTest {
        val session = testSession(Samples.liveCorner.copy(goal = Goal.KoLive), solver = solver)
        assertTrue(session.pass())
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun searchPathsAppearWhileWaitingForReply() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val solver = object : Solver {
            override suspend fun solve(input: SolverInput): SolverResult {
                input.onPath("白下 B3 -> 黑下 A3 -> 黑勝")
                started.complete(Unit)
                finish.await()
                return SolverResult.Resist(Action.Pass)
            }
        }
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        started.await()
        val waiting = session.state.value
        assertEquals(PlayStatus.WaitingForReply, waiting.status)
        assertEquals(listOf("白下 B3 -> 黑下 A3 -> 黑勝"), waiting.searchPaths)
        finish.complete(Unit)
        session.waitForIdle()
    }

    @Test
    fun decisionTreeFillsFromPvWhileWaiting() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val solver = object : Solver {
            override suspend fun solve(input: SolverInput): SolverResult {
                input.onPath("白下 B3 -> 黑下 A3 -> 黑勝")
                input.onPv(Action.Move(pt("B3")), Action.Move(pt("A3")), "黑勝", false)
                started.complete(Unit)
                finish.await()
                return SolverResult.Resist(Action.Pass)
            }
        }
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        started.await()
        val tree = session.state.value.decisionTree
        assertEquals(1, tree.pathCount)
        assertEquals(1, tree.leafCount)
        assertEquals("白下 B3", tree.lines[0].text)
        assertEquals("黑下 A3", tree.lines[1].text)
        assertEquals("黑勝", tree.lines[2].text)
        finish.complete(Unit)
        session.waitForIdle()
    }

    @Test
    fun liveSolverDecisionTreeUsesWinsNotSuccess() = runTest {
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        session.waitForIdle()
        val texts = session.state.value.decisionTree.lines.joinToString("\n") { it.text }
        assertTrue(session.state.value.decisionTree.leafCount > 0, "expected 決策樹, was:\n$texts")
        assertTrue("黑勝" in texts || "白勝" in texts, texts)
        assertTrue("成功" !in texts, texts)
        assertTrue("失敗" !in texts, texts)
        assertTrue(texts.lines().none { it.startsWith("1.") })
    }

    @Test
    fun openingWhiteLifeIsTheVitalEyePoint() {
        assertEquals(pt("C5"), findOpeningWhiteLife(openingWhiteLifeProblem()))
    }

    @Test
    fun whiteActionsVerifyOpeningLifeBeforeADecoyCapture() {
        val problem = openingWhiteLifeProblem()
        val afterBlack = requireNotNull(Position.initial(problem).play(pt("B5"), StoneColor.Black))
        val hint = findOpeningWhiteLife(problem)
        val ordered = actions(afterBlack, StoneColor.White, problem, hintWhite = hint)
        assertEquals(Action.Move(pt("C5")), ordered.first(), ordered.toString())
    }

    @Test
    fun sessionDoesNotPassOpeningWhiteLifeIntoSearch() = runTest {
        var seen: Point? = pt("A1")
        val solver = object : Solver {
            override suspend fun solve(input: SolverInput): SolverResult {
                seen = input.hintWhite
                return SolverResult.Resist(Action.Pass)
            }
        }
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        session.waitForIdle()
        assertNull(seen)
    }

    @Test
    fun extraFarStoneDoesNotChangeTheRefutation() = runTest {
        val base = openingWhiteLifeProblem()
        val extra = base.copy(stones = base.stones + (pt("C1") to StoneColor.Black))
        val a = testSession(base, solver = AlphaBetaSolver(maxDepth = 12))
        val b = testSession(extra, solver = AlphaBetaSolver(maxDepth = 12))
        assertTrue(a.tryMove(pt("D1")))
        assertTrue(b.tryMove(pt("D1")))
        a.waitForIdle()
        b.waitForIdle()
        assertEquals(a.state.value.status, b.state.value.status)
        assertEquals(a.state.value.lastMove, b.state.value.lastMove)
    }

    @Test
    fun farWhiteRepliesAreNotAllSearchedAfterANullMove() = runTest {
        val problem = openingWhiteLifeProblem()
        val session = testSession(problem, solver = AlphaBetaSolver(maxDepth = 12))
        assertTrue(session.tryMove(pt("C5")))
        session.waitForIdle()
        val firstWhite = session.state.value.searchPaths.mapNotNull { path ->
            Regex("""^白下 ([A-T]\\d+)""").find(path)?.groupValues?.get(1)
        }.toSet()
        val far = setOf("C1", "D1", "B2", "C2")
        assertTrue(
            firstWhite.intersect(far).size <= 1,
            "equivalent 空手 should not each grow a 搜尋路徑, firstWhite=$firstWhite",
        )
    }

    @Test
    fun monteCarloRanksTheImmediateLivingMoveFirst() {
        val problem = openingWhiteLifeProblem()
        val position = Position.initial(problem)
        val candidates = listOf(pt("A2"), pt("B5"), pt("C5"), pt("D5"), pt("B3"))
        val ranked = rankMovesByPlayout(
            position = position,
            toPlay = StoneColor.White,
            problem = problem,
            candidates = candidates,
            random = Random(1),
            playouts = 8,
        )
        assertEquals(pt("C5"), ranked.first())
    }

    @Test
    fun searchPathCountIsTheRealTotalPastTheDisplayedCap() = runTest {
        val solver = object : Solver {
            override suspend fun solve(input: SolverInput): SolverResult {
                repeat(1005) { i ->
                    input.onPath("白下 A1 -> 白勝 #$i")
                }
                input.onPathsComplete()
                return SolverResult.Resist(Action.Pass)
            }
        }
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(1005, snap.searchPathCount)
        assertEquals(1000, snap.searchPaths.size)
    }

    @Test
    fun pathsCompleteMarksPickingReply() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val solver = object : Solver {
            override suspend fun solve(input: SolverInput): SolverResult {
                input.onPath("白下 B3 -> 黑下 A3 -> 黑勝")
                input.onPathsComplete()
                started.complete(Unit)
                finish.await()
                return SolverResult.Resist(Action.Pass)
            }
        }
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        started.await()
        assertTrue(session.state.value.pickingReply)
        finish.complete(Unit)
        session.waitForIdle()
        assertTrue(!session.state.value.pickingReply)
    }

    @Test
    fun passDuringSearchAppliesWhitePass() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val solver = object : Solver {
            override suspend fun solve(input: SolverInput): SolverResult {
                started.complete(Unit)
                finish.await()
                return SolverResult.Resist(Action.Move(pt("B3")))
            }
        }
        val session = testSession(openingWhiteLifeProblem(), solver = solver)
        assertTrue(session.tryMove(pt("B5")))
        started.await()
        assertTrue(session.pass())
        finish.complete(Unit)
        session.waitForIdle()
        assertEquals(PlayStatus.InProgress, session.state.value.status)
        assertNull(session.state.value.stones[pt("B3")])
        assertEquals(StoneColor.Black, session.state.value.stones[pt("B5")])
        assertTrue(session.state.value.lastMoveIsPass)
    }

    @Test
    fun depthExhaustedKeepsBoardWithoutAWhiteMove() = runTest {
        val session = testSession(
            openingWhiteLifeProblem(),
            solver = ImmediateTimeoutSolver,
        )
        assertTrue(session.tryMove(pt("B5")))
        session.waitForIdle()
        val snap = session.state.value
        assertEquals(PlayStatus.WaitingForReply, snap.status)
        assertEquals(pt("B5"), snap.lastMove)
        assertNull(snap.stones[pt("C5")])
        assertTrue(snap.canUndo)
        assertTrue(session.undo())
        assertEquals(PlayStatus.InProgress, session.state.value.status)
        assertTrue(session.state.value.searchPaths.isEmpty())
    }

    @Test
    fun koKillProblemSucceedsOnUnconditionalDead() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.KoKill,
                targets = "B2",
            ),
            solver = solver,
        )
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
        assertNull(session.state.value.stones[pt("B2")])
    }

    @Test
    fun capturingTheLastLibertyStillSucceedsWithRealSolver() = runTest {
        val session = testSession(
            cornerProblem(
                black = "A2,B1,C2",
                white = "B2",
                goal = Goal.Kill,
                targets = "B2",
            ),
            solver = solver,
        )
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
        assertNull(session.state.value.stones[pt("B2")])
    }
}
