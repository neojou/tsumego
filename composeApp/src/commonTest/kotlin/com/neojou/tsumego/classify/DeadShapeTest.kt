package com.neojou.tsumego.classify

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.Outcome
import com.neojou.tsumego.board.isSuccess
import com.neojou.tsumego.board.Position
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.boxedProblem
import com.neojou.tsumego.pt
import com.neojou.tsumego.solve.Action
import com.neojou.tsumego.ScriptedSolver
import com.neojou.tsumego.KILL_8K_JSON
import com.neojou.tsumego.SMALL_TRICK_JSON
import com.neojou.tsumego.smallTrickPlayable
import com.neojou.tsumego.library.ProblemLibrary
import com.neojou.tsumego.library.ProblemLoad
import com.neojou.tsumego.play.PlayStatus
import com.neojou.tsumego.testSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun twoPointEyeKill() = boxedProblem(
    left = "A",
    right = "F",
    bottom = 1,
    top = 6,
    black = "A2,A3,A4,A5,B2,B5,C2,C5,D2,D3,D4,D5,E2,E3,E4,E5",
    white = "B4,C4",
    goal = Goal.Kill,
    targets = "B4,C4",
)

class DeadShapeTest {
    @Test
    fun twoPointEyeCannotBecomeUnconditionalLiveEvenIfOwnerFills() {
        val problem = twoPointEyeKill()
        val pos = Position.initial(problem)
        assertTrue(bensonAlive(pos, StoneColor.White).isEmpty(), "already Benson: ${bensonAlive(pos, StoneColor.White)}")
        assertTrue(!ownerCanForceLife(pos, problem.targets), "owner must not be able to make two eyes")
        assertEquals(Outcome.UnconditionalDead, classify(pos, problem.targets, bothPassed = false))
    }

    @Test
    fun killSucceedsWhenWhiteCannotMakeTwoEyes() = runTest {
        val problem = twoPointEyeKill()
        val session = testSession(problem)
        assertTrue(session.tryMove(pt("B3")))
        session.waitForIdle()
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun s19MakesTwoEyesWhenBlackTenukiAtS13() {
        val problem = smallTrickPlayable()
        var pos = Position.initial(problem)
        pos = requireNotNull(pos.play(pt("S13"), StoneColor.Black))
        assertEquals(pt("S19"), firstOwnerMoveToTwoEyes(pos, problem.targets))
        pos = requireNotNull(pos.play(pt("S19"), StoneColor.White))
        assertTrue(ownerCanForceLife(pos, problem.targets), "S19 should allow 兩眼做活 if black 脫先")
    }

    @Test
    fun s17IsNotTheLivingMoveWhenBlackTenukiAtS13() {
        val problem = smallTrickPlayable()
        val start = Position.initial(problem)
        val pos = requireNotNull(start.play(pt("S13"), StoneColor.Black))
        assertTrue(firstOwnerMoveToTwoEyes(pos, problem.targets) != pt("S17"))
        assertTrue(isAwayFromTargets(start, pt("S13"), problem.targets))
        assertTrue(!isAwayFromTargets(start, pt("S19"), problem.targets))
    }

    @Test
    fun t16IsLivingMoveAfterS19() {
        val problem = smallTrickPlayable()
        val pos = requireNotNull(Position.initial(problem).play(pt("S19"), StoneColor.Black))
        assertEquals(pt("T16"), firstOwnerMoveToTwoEyes(pos, problem.targets))
    }

    @Test
    fun afterS19R19S15T16S17IsKillSuccess() {
        val problem = smallTrickPlayable()
        var pos = Position.initial(problem)
        val plays = listOf(
            StoneColor.Black to "S19",
            StoneColor.White to "R19",
            StoneColor.Black to "S15",
            StoneColor.White to "T16",
            StoneColor.Black to "S17",
        )
        for ((color, label) in plays) {
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
        }
        val outcome = classify(pos, problem.targets, bothPassed = false)
        assertEquals(Outcome.UnconditionalDead, outcome)
        assertTrue(problem.goal.isSuccess(outcome))
    }

    @Test
    fun t16MakesTwoEyesAfterS19R19S15() {
        val problem = smallTrickPlayable()
        var pos = Position.initial(problem)
        val plays = listOf(
            StoneColor.Black to "S19",
            StoneColor.White to "R19",
            StoneColor.Black to "S15",
        )
        for ((color, label) in plays) {
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
        }
        assertEquals(pt("T16"), firstOwnerMoveToTwoEyes(pos, problem.targets))
        pos = requireNotNull(pos.play(pt("T16"), StoneColor.White))
        assertTrue(ownerCanForceLife(pos, problem.targets), "T16 should allow 兩眼做活 if black 脫先")
    }

    @Test
    fun s17IsNotTheLivingMoveAfterS19R19S15() {
        val problem = smallTrickPlayable()
        var pos = Position.initial(problem)
        val plays = listOf(
            StoneColor.Black to "S19",
            StoneColor.White to "R19",
            StoneColor.Black to "S15",
        )
        for ((color, label) in plays) {
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
        }
        assertEquals(pt("T16"), firstOwnerMoveToTwoEyes(pos, problem.targets))
        assertTrue(firstOwnerMoveToTwoEyes(pos, problem.targets) != pt("S17"))
    }

    @Test
    fun smallTrickConnectThenT16S18IsUnsettledNotSeki() {
        val problem = smallTrickPlayable()
        var pos = Position.initial(problem)
        val plays = listOf(
            StoneColor.Black to "S19",
            StoneColor.White to "S17",
            StoneColor.Black to "T16",
            StoneColor.White to "S18",
        )
        for ((color, label) in plays) {
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
        }
        assertTrue(ownerCanForceLife(pos, problem.targets), "S15 still kills two-eye potential")
        assertEquals(Outcome.Unsettled, classify(pos, problem.targets, bothPassed = false))
        pos = requireNotNull(pos.play(pt("S15"), StoneColor.Black))
        assertTrue(!ownerCanForceLife(pos, problem.targets))
        assertEquals(Outcome.UnconditionalDead, classify(pos, problem.targets, bothPassed = false))
    }

    @Test
    fun smallTrickSealedGroupIsUnconditionalDead() {
        val problem = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(SMALL_TRICK_JSON)).problem
        var pos = Position.initial(problem)
        val plays = listOf(
            StoneColor.Black to "S19",
            StoneColor.White to "S17",
            StoneColor.Black to "R19",
            StoneColor.White to "S15",
            StoneColor.Black to "S14",
            StoneColor.White to "S18",
            StoneColor.Black to "T14",
            StoneColor.White to "T15",
        )
        for ((color, label) in plays) {
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
        }
        assertTrue(!ownerCanForceLife(pos, problem.targets))
        assertEquals(Outcome.UnconditionalDead, classify(pos, problem.targets, bothPassed = false))
    }

    @Test
    fun smallTrickSequenceWithoutTwoEyesIsKillSuccess() = runTest {
        val problem = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(SMALL_TRICK_JSON)).problem
        val session = testSession(
            problem,
            solver = ScriptedSolver(
                listOf(
                    Action.Move(pt("S17")),
                    Action.Move(pt("S15")),
                    Action.Move(pt("S18")),
                    Action.Move(pt("T15")),
                ),
            ),
        )
        for (move in listOf("S19", "R19", "S14", "T14")) {
            if (session.state.value.status != PlayStatus.InProgress) break
            assertTrue(
                session.tryMove(pt(move)),
                "black $move status=${session.state.value.status} last=${session.state.value.lastMove}",
            )
            session.waitForIdle()
        }
        assertEquals(PlayStatus.Success, session.state.value.status)
    }

    @Test
    fun atariStringIsNotBensonAliveViaANeighbourWhiteStone() {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_8K_JSON)).problem
        var pos = Position.initial(loaded)
        for ((color, label) in listOf(
            StoneColor.Black to "S18",
            StoneColor.White to "T18",
            StoneColor.Black to "S19",
            StoneColor.White to "S17",
        )) {
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
        }
        val alive = bensonAlive(pos, StoneColor.White)
        assertTrue(
            loaded.targets.none { it in alive },
            "Benson ${alive.sorted().joinToString { it.label }}",
        )
        assertTrue(
            classify(pos, loaded.targets, bothPassed = false) != Outcome.UnconditionalLive,
            "outcome=${classify(pos, loaded.targets, bothPassed = false)}",
        )
    }

    @Test
    fun kill8KoTakeIsKoKillNotUnconditionalDead() {
        val loaded = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(KILL_8K_JSON)).problem
        var pos = Position.initial(loaded)
        for ((color, label) in listOf(
            StoneColor.Black to "T18",
            StoneColor.White to "S18",
            StoneColor.Black to "T17",
            StoneColor.White to "T19",
            StoneColor.Black to "S19",
        )) {
            pos = requireNotNull(pos.play(pt(label), color)) { "illegal $color $label" }
        }
        assertEquals(Outcome.KoKill, classify(pos, loaded.targets, bothPassed = false))
        assertTrue(!loaded.goal.isSuccess(Outcome.KoKill))
    }
}
