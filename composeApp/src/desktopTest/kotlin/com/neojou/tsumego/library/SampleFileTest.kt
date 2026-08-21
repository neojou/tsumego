package com.neojou.tsumego.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SampleFileTest {
    @Test
    fun listedSampleMatchesDocs15KKillFile() {
        val text = loadProblemFile("15K-kill.tsumego.json")
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        val sample = Samples.all.first { it.name == "15K 殺棋" }
        assertEquals(ok.problem, sample.problem)
    }

    @Test
    fun listedSampleMatchesDocs13KKillFile() {
        val text = loadProblemFile("13K-kill.tsumego.json")
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        val sample = Samples.all.first { it.name == "13K 殺棋" }
        assertEquals(ok.problem, sample.problem)
    }

    @Test
    fun listedSampleMatchesDocs8KKillFile() {
        val text = loadProblemFile("8K-kill.tsumego.json")
        val ok = assertIs<ProblemLoad.Ok>(ProblemLibrary.decode(text))
        val sample = Samples.all.first { it.name == "8K 殺棋" }
        assertEquals(ok.problem, sample.problem)
    }
}

private fun loadProblemFile(name: String): String {
    val candidates = listOf(
        File("../docs/$name"),
        File("docs/$name"),
        File("composeApp/../docs/$name"),
    )
    val file = candidates.firstOrNull { it.exists() }
        ?: error("$name not found from ${File(".").absolutePath}")
    return file.readText()
}
