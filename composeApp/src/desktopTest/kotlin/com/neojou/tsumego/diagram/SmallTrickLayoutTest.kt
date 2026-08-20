package com.neojou.tsumego.diagram

import com.neojou.tsumego.board.EdgeKind
import com.neojou.tsumego.pt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        assertTrue(draft.stones.isEmpty())
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
