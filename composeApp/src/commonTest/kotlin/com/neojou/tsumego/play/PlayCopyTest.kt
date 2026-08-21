package com.neojou.tsumego.play

import com.neojou.tsumego.board.Goal
import kotlin.test.Test
import kotlin.test.assertEquals


class PlayCopyTest {
    @Test
    fun killHeadingIsBlackFirstKillWhite() {
        assertEquals("黑先殺白", Goal.Kill.playHeading())
        assertEquals("黑先做活", Goal.Live.playHeading())
    }

    @Test
    fun searchPathsAreNumberedFromOne() {
        val numbered = numberedSearchPaths(
            listOf(
                "白下 B3 -> 黑下 A3 -> 結果 成功",
                "白停 -> 黑下 B3 -> 結果 成功",
            ),
        )
        assertEquals("1. 白下 B3 -> 黑下 A3 -> 結果 成功", numbered[0])
        assertEquals("2. 白停 -> 黑下 B3 -> 結果 成功", numbered[1])
    }

    @Test
    fun searchPathCountLabelShowsActualTotalWhenTruncated() {
        assertEquals("搜尋路徑數目： 12", searchPathCountLabel(displayed = 12, total = 12))
        assertEquals("搜尋路徑數目： 2500（列出 1000）", searchPathCountLabel(displayed = 1000, total = 2500))
    }

    @Test
    fun redoLabelIsRedo() {
        assertEquals("重做", redoLabel())
    }

    @Test
    fun emptyPlayHintNamesImportOpenAndSamples() {
        assertEquals(
            "請用 Import 匯入題目圖片，或 File → Open 打開題目檔，或直接選 File → Samples 下的例題開始玩",
            emptyPlayHint(),
        )
    }

    @Test
    fun thinkingSecondsFloorElapsedMillis() {
        assertEquals(0, thinkingSeconds(0))
        assertEquals(2, thinkingSeconds(2_999))
        assertEquals(12, thinkingSeconds(12_001))
    }
}
