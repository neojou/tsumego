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
            targets = emptySet(),
        )
        val reader = DiagramReader { _, _ -> base }
        val draft = readDiagram(reader, byteArrayOf(1, 2, 3), StoneColor.White)
        assertEquals(StoneColor.White, draft.stones[pt("A2")])
        assertEquals(StoneColor.White, draft.stones[pt("B1")])
        assertEquals(StoneColor.Black, draft.stones[pt("C2")])
        assertEquals(emptySet(), draft.targets)
        val marked = draft.copy(goal = Goal.Kill, targets = setOf(pt("A2")))
        assertEquals(pt("A2"), marked.targets.single())
        assertEquals(StoneColor.White, marked.stones[pt("A2")])
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
        draft = draft.toggleTarget(pt("A1"))
        assertEquals(setOf(pt("A1"), pt("A2"), pt("B1")), draft.targets)
        draft = draft.toggleTarget(pt("A2"))
        assertEquals(setOf(pt("A1"), pt("B1")), draft.targets)
    }

    @Test
    fun confirmRejectsSekiWithoutBothColors() {
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
        assertNotNull(draft.validationError())
        assertTrue(draft.validationError()!!.contains("雙活"))
    }

    @Test
    fun emptyReaderLeavesBoardEmptyWithTheImage() {
        val bytes = byteArrayOf(9, 9, 9)
        val draft = EmptyDiagramReader.read(bytes, StoneColor.Black)
        assertTrue(draft.stones.isEmpty())
        assertEquals(bytes, draft.imageBytes)
    }
}
