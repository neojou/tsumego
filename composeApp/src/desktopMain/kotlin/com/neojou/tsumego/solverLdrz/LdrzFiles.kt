package com.neojou.tsumego.solverLdrz

import com.neojou.tsumego.io.platformDirectoryMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileFilter
import kotlin.coroutines.resume

actual suspend fun ldrzPickProblemJson(): LdrzPickedFile? =
    suspendCancellableCoroutine { cont ->
        SwingUtilities.invokeLater {
            try {
                val memory = platformDirectoryMemory()
                val lastDir = memory.lastDirectory()?.let { File(it) }?.takeIf { it.isDirectory }
                val tsumegoDir = File(findRepoRoot(), "docs/refs/study-LD-RZ/tsumego")
                val chooser = JFileChooser().apply {
                    fileFilter = object : FileFilter() {
                        override fun accept(f: File): Boolean =
                            f.isDirectory || f.name.endsWith(".json", ignoreCase = true)

                        override fun getDescription(): String = "study-LD-RZ JSON (*.json)"
                    }
                    currentDirectory = when {
                        tsumegoDir.isDirectory -> tsumegoDir
                        lastDir != null -> lastDir
                        else -> findRepoRoot()
                    }
                }
                val code = chooser.showOpenDialog(null)
                if (code == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    memory.rememberFile(file.absolutePath)
                    cont.resume(LdrzPickedFile(name = file.name, text = file.readText()))
                } else {
                    cont.resume(null)
                }
            } catch (_: Throwable) {
                cont.resume(null)
            }
        }
    }

actual suspend fun ldrzWriteResultFiles(stem: String, json: String, sgf: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(findRepoRoot(), "result")
            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext "無法建立 result/"
            }
            File(dir, "result_$stem.json").writeText(json)
            File(dir, "uct_tree_$stem.sgf").writeText(sgf)
            null
        } catch (e: Exception) {
            e.message ?: "無法寫入 result/"
        }
    }

actual suspend fun ldrzReadResultFiles(stem: String): Pair<String, String>? =
    withContext(Dispatchers.IO) {
        val dir = File(findRepoRoot(), "result")
        val jsonFile = File(dir, "result_$stem.json")
        val sgfFile = File(dir, "uct_tree_$stem.sgf")
        if (!jsonFile.isFile) return@withContext null
        val json = jsonFile.readText()
        val sgf = if (sgfFile.isFile) sgfFile.readText() else ""
        json to sgf
    }

internal fun findRepoRoot(): File {
    var dir: File? = File(System.getProperty("user.dir") ?: ".")
    repeat(10) {
        val cur = dir ?: return File(".")
        if (File(cur, "settings.gradle.kts").isFile || File(cur, "settings.gradle").isFile) {
            return cur
        }
        dir = cur.parentFile
    }
    return File(System.getProperty("user.dir") ?: ".")
}
