package com.neojou.tsumego.solverLdrz

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LdrzChaoFileTest {
    @Test
    fun parseChaoP088IfPresent() {
        val file = chaoFile() ?: return
        val loaded = LdrzJson.parse(file.readText(), file.name)
        val ok = assertIs<LdrzLoad.Ok>(loaded)
        assertEquals("chao_vol1_p088", ok.problem.stem)
        assertTrue(ok.problem.stones.isNotEmpty())
        assertTrue(ok.problem.region.isNotEmpty())
        assertTrue(ok.problem.blackCrucial.isNotEmpty())
        val pos = ok.problem.toPosition()
        assertTrue(pos.stones.isNotEmpty())
    }

    private fun chaoFile(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(10) {
            val cur = dir ?: return null
            val candidate = File(cur, "docs/refs/study-LD-RZ/tsumego/chao_vol1_p088.json")
            if (candidate.isFile) return candidate
            dir = cur.parentFile
        }
        return null
    }
}
