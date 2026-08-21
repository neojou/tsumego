package com.neojou.tsumego.io

import com.neojou.tsumego.diagram.DiagramReader
import com.neojou.tsumego.diagram.desktop.DesktopDiagramReader
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume

actual suspend fun openProblemText(): String? = withChooser(
    save = false,
    filter = jsonFilter(),
) { it.readText() }

actual suspend fun saveProblemText(suggestedName: String, content: String): Boolean {
    val file = withChooser(
        save = true,
        filter = jsonFilter(),
        suggested = suggestedName,
    ) { it }
    if (file == null) return false
    val target = if (file.name.endsWith(".tsumego.json")) file else File(file.parentFile, file.name + ".tsumego.json")
    target.writeText(content)
    return true
}

actual suspend fun openDiagramImage(): PickedImage? = withChooser(
    save = false,
    filter = FileNameExtensionFilter("棋譜圖 (png, jpg)", "png", "jpg", "jpeg", "webp"),
) { file -> PickedImage(file.readBytes(), file.name) }

actual fun platformDiagramReader(): DiagramReader = DesktopDiagramReader()

private fun jsonFilter(): FileFilter = object : FileFilter() {
    override fun accept(f: File): Boolean = f.isDirectory || f.name.endsWith(".tsumego.json") || f.name.endsWith(".json")
    override fun getDescription(): String = "題目檔 (*.tsumego.json)"
}

private suspend fun <T> withChooser(
    save: Boolean,
    filter: FileFilter,
    suggested: String? = null,
    read: (File) -> T,
): T? = suspendCancellableCoroutine { cont ->
    SwingUtilities.invokeLater {
        try {
            val memory = platformDirectoryMemory()
            val lastDir = memory.lastDirectory()?.let { File(it) }?.takeIf { it.isDirectory }
            val chooser = JFileChooser().apply {
                fileFilter = filter
                if (lastDir != null) currentDirectory = lastDir
                if (suggested != null) {
                    selectedFile = if (lastDir != null) File(lastDir, File(suggested).name) else File(suggested)
                }
            }
            val code = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
            if (code == JFileChooser.APPROVE_OPTION) {
                memory.rememberFile(chooser.selectedFile.absolutePath)
                cont.resume(read(chooser.selectedFile))
            } else {
                cont.resume(null)
            }
        } catch (_: Throwable) {
            cont.resume(null)
        }
    }
}
