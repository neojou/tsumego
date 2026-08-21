package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.Goal
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.cornerProblem
import com.neojou.tsumego.pt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagramDraftTest {
    @Test
    fun problemRoundTripsToConfirmDraftForEdit() {
        val problem = cornerProblem(
            black = "A2,B1,C2",
            white = "B2",
            goal = Goal.Kill,
            targets = "B2",
        )
        val draft = problem.toConfirmDraft()
        assertEquals(problem.rect, draft.rect)
        assertEquals(problem.edges, draft.edges)
        assertEquals(problem.stones, draft.stones)
        assertEquals(Goal.Kill, draft.goal)
        assertEquals(problem.targets, draft.targets)
        val edited = draft.copy(goal = Goal.KoKill)
        assertEquals(Goal.Kill, edited.toProblem().goal)
        assertEquals(problem.stones, edited.toProblem().stones)
    }

    @Test
    fun whiteFirstFlipsStonesAndKeepsTargetPoints() {
        val problem = cornerProblem(
            black = "A2,B1",
            white = "C2",
            goal = Goal.Kill,
            targets = "C2",
        )
        val base = ConfirmDraft(
            imageBytes = byteArrayOf(1, 2, 3),
            rect = problem.rect,
            edges = problem.edges,
            stones = problem.stones,
            goal = Goal.Live,
            targets = setOf(pt("C2")),
        )
        val reader = DiagramReader { _, _ -> base }
        val draft = readDiagram(reader, byteArrayOf(1, 2, 3), StoneColor.White)
        assertEquals(StoneColor.White, draft.stones[pt("A2")])
        assertEquals(StoneColor.White, draft.stones[pt("B1")])
        assertEquals(StoneColor.Black, draft.stones[pt("C2")])
        assertEquals(Goal.Kill, draft.goal)
        assertEquals(setOf(pt("C2")), draft.targets)
        assertNotNull(draft.validationError())
        assertTrue(draft.validationError()!!.contains("白"), draft.validationError())
        val marked = draft.copy(targets = setOf(pt("A2")))
        assertEquals(pt("A2"), marked.targets.single())
        assertEquals(StoneColor.White, marked.stones[pt("A2")])
        assertEquals(Goal.Kill, marked.toProblem().goal)
        assertNull(marked.validationError())
    }

    @Test
    fun cyclingStoneGoesEmptyBlackWhite() {
        val problem = cornerProblem(
            black = "",
            white = "",
            goal = Goal.Kill,
            targets = "B2",
        )
        var draft = emptyDraft(null).copy(rect = problem.rect, edges = problem.edges)
        draft = draft.cycleStone(pt("B2"))
        assertEquals(StoneColor.Black, draft.stones[pt("B2")])
        draft = draft.cycleStone(pt("B2"))
        assertEquals(StoneColor.White, draft.stones[pt("B2")])
        draft = draft.cycleStone(pt("B2"))
        assertNull(draft.stones[pt("B2")])
    }

    @Test
    fun markingATargetExpandsTheStringAndUnmarksOnePoint() {
        val problem = cornerProblem(
            black = "A1,A2,B1",
            white = "",
            goal = Goal.Live,
            targets = "A1,A2,B1",
        )
        var draft = ConfirmDraft(null, problem.rect, problem.edges, problem.stones)
        draft = draft.applyClick(pt("A1"), targetMode = true)
        assertEquals(setOf(pt("A1"), pt("A2"), pt("B1")), draft.targets)
        draft = draft.applyClick(pt("A2"), targetMode = true)
        assertEquals(setOf(pt("A1"), pt("B1")), draft.targets)
    }

    @Test
    fun targetModeCanMarkTwoSeparateStrings() {
        val problem = cornerProblem(
            files = 5,
            ranks = 3,
            black = "A1,A2,E2,E3",
            white = "C2",
            goal = Goal.Live,
            targets = "A1,A2",
        )
        var draft = ConfirmDraft(null, problem.rect, problem.edges, problem.stones)
        draft = draft.applyClick(pt("C2"), targetMode = true)
        assertEquals(setOf(pt("C2")), draft.targets)
        assertEquals(Goal.Kill, draft.goal)
        assertEquals(null, draft.validationError())
    }

    @Test
    fun cycleModeDoesNotRecordTargets() {
        val problem = cornerProblem(
            black = "A2",
            white = "",
            goal = Goal.Live,
            targets = "A2",
        )
        var draft = ConfirmDraft(null, problem.rect, problem.edges, problem.stones)
        draft = draft.applyClick(pt("B2"), targetMode = false)
        assertTrue(draft.targets.isEmpty())
        assertNotNull(draft.validationError())
    }

    @Test
    fun confirmRejectsBlackTargetsOnKill() {
        val problem = cornerProblem(
            black = "A2",
            white = "C2",
            goal = Goal.Kill,
            targets = "C2",
        )
        val draft = ConfirmDraft(
            imageBytes = null,
            rect = problem.rect,
            edges = problem.edges,
            stones = problem.stones,
            goal = Goal.Seki,
            targets = setOf(pt("A2")),
        )
        assertEquals(Goal.Kill, draft.toProblem().goal)
        assertNotNull(draft.validationError())
        assertTrue(draft.validationError()!!.contains("白"), draft.validationError())
    }

    @Test
    fun emptyDraftIsKill() {
        val draft = emptyDraft(null)
        assertEquals(Goal.Kill, draft.goal)
        assertEquals(Goal.Kill, draft.toProblem().goal)
    }

    @Test
    fun emptyReaderLeavesBoardEmptyWithTheImage() {
        val bytes = byteArrayOf(9, 9, 9)
        val draft = EmptyDiagramReader.read(bytes, StoneColor.Black)
        assertTrue(draft.stones.isEmpty())
        assertEquals(bytes, draft.imageBytes)
    }
}
