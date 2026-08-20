package com.neojou.tsumego.diagram.desktop

import com.neojou.tsumego.board.StoneColor
import com.neojou.tsumego.diagram.ConfirmDraft
import com.neojou.tsumego.diagram.DiagramReader
import com.neojou.tsumego.diagram.emptyDraft

/**
 * Desktop uses the same lattice sampling as Wasm; White First flip happens in [readDiagram].
 */
class DesktopDiagramReader : DiagramReader {
    override fun read(image: ByteArray, diagramFirst: StoneColor): ConfirmDraft = emptyDraft(image)
}
