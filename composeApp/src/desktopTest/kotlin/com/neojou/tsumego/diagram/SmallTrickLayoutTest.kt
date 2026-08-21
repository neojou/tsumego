package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.diagram.desktop.DesktopDiagramReader
import com.neojou.tsumego.pt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmallTrickLayoutTest {
    private val bytes: ByteArray = loadSmallTrick()

    @Test
    fun croppedUpperRightDiagramIsOToTAnd14To19() {
        val layout = detectDiagramLayout(bytes)
        assertNotNull(layout)
        assertEquals(pt("O14").file, layout.rect.left)
        assertEquals(pt("T19").file, layout.rect.right)
        assertEquals(14, layout.rect.bottom)
        assertEquals(19, layout.rect.top)
        assertEquals(EdgeKind.Wall, layout.edges.left)
        assertEquals(EdgeKind.Real, layout.edges.right)
        assertEquals(EdgeKind.Wall, layout.edges.bottom)
        assertEquals(EdgeKind.Real, layout.edges.top)
    }

    @Test
    fun emptyDraftKeepsThePhotoCropEvenWithoutFilledStones() {
        val draft = emptyDraft(bytes)
        assertEquals(pt("O14").file, draft.rect.left)
        assertEquals(pt("T19").file, draft.rect.right)
        assertEquals(14, draft.rect.bottom)
        assertEquals(19, draft.rect.top)
        val grid = draft.imageGrid
        assertNotNull(grid)
        val overlay = overlayLayout(
            canvasWidth = 908f,
            canvasHeight = 946f,
            imageWidth = 908f,
            imageHeight = 946f,
            imageGrid = grid,
            rect = draft.rect,
        )
        assertEquals(pt("T18"), overlay.hit(729f, 322f))
    }

    @Test
    fun photoStonesMatchThePrintedColorsBeforeFlip() {
        val draft = emptyDraft(bytes)
        // Independent of the code: T18 is the black stone on the right edge,
        // Q19 the top white stone, O19 an empty intersection.
        assertEquals(StoneColor.Black, draft.stones[pt("T18")])
        assertEquals(StoneColor.White, draft.stones[pt("Q19")])
        assertNull(draft.stones[pt("O19")])
    }

    @Test
    fun wasmReaderKeepsTheCropButDoesNotFillStones() {
        val draft = EmptyDiagramReader.read(bytes, StoneColor.Black)
        assertEquals(pt("O14").file, draft.rect.left)
        assertEquals(pt("T19").file, draft.rect.right)
        assertEquals(14, draft.rect.bottom)
        assertEquals(19, draft.rect.top)
        assertNotNull(draft.imageGrid)
        assertTrue(draft.stones.isEmpty())
    }

    @Test
    fun whiteFirstFlipsThePrintedStoneColors() {
        val draft = readDiagram(DesktopDiagramReader(), bytes, StoneColor.White)
        assertEquals(StoneColor.White, draft.stones[pt("T18")])
        assertEquals(StoneColor.Black, draft.stones[pt("Q19")])
        assertNull(draft.stones[pt("O19")])
    }

    @Test
    fun whiteFirstCanMarkWhiteStringsAndConfirmKill() {
        var draft = readDiagram(DesktopDiagramReader(), bytes, StoneColor.White)
        assertEquals(com.neojou.tsumego.board.Goal.Kill, draft.goal)
        draft = draft.applyClick(pt("T18"), targetMode = true)
        assertTrue(pt("T18") in draft.targets)
        assertEquals(StoneColor.White, draft.stones[pt("T18")])
        assertEquals(null, draft.validationError())
        assertEquals(com.neojou.tsumego.board.Goal.Kill, draft.toProblem().goal)
        val blackMarked = draft.applyClick(pt("Q19"), targetMode = true)
        assertNotNull(blackMarked.validationError())
        assertTrue(blackMarked.validationError()!!.contains("白"))
    }

    @Test
    fun clickOnTheT18StoneHitsT18NotA19x19Index() {
        val detected = detectDiagramLayout(bytes)
        assertNotNull(detected)
        val overlay = overlayLayout(
            canvasWidth = 908f,
            canvasHeight = 946f,
            imageWidth = 908f,
            imageHeight = 946f,
            imageGrid = detected.imageGrid,
            rect = detected.rect,
        )
        // T18 is the black stone on the real right edge, second line from the top.
        assertEquals(pt("T18"), overlay.hit(729f, 322f))
        assertTrue(overlay.hit(800f, 50f) != pt("T19"))
    }
}

private fun loadSmallTrick(): ByteArray {
    val candidates = listOf(
        File("../docs/tests/small_trick.png"),
        File("docs/tests/small_trick.png"),
        File("composeApp/../docs/tests/small_trick.png"),
    )
    val file = candidates.firstOrNull { it.exists() }
        ?: error("small_trick.png not found from ${File(".").absolutePath}")
    return file.readBytes()
}
