package com.neojou.tsumego.io

import com.neojou.tsumego.diagram.DiagramReader

data class PickedImage(
    val bytes: ByteArray,
    val name: String,
)

expect suspend fun openProblemText(): String?

expect suspend fun saveProblemText(suggestedName: String, content: String): Boolean

expect suspend fun openDiagramImage(): PickedImage?

expect fun platformDiagramReader(): DiagramReader
