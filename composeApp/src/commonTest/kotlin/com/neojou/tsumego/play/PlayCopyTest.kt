package com.neojou.tsumego.play

import com.neojou.tsumego.AppVersion
import com.neojou.tsumego.board.Goal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class PlayCopyTest {
    @Test
    fun killHeadingIsBlackFirstKillWhite() {
        assertEquals("黑先殺白", Goal.Kill.playHeading())
        assertEquals("黑先做活", Goal.Live.playHeading())
    }

    @Test
    fun decisionTreeTitleIsDecisionTree() {
        assertEquals("決策樹", decisionTreeTitle())
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

    @Test
    fun aboutCopyPutsProductAboveVersion() {
        assertEquals("關於", aboutTitle())
        assertEquals("詰碁", AppVersion.APP_NAME)
        assertEquals("圍棋死活訓練機", AppVersion.SUMMARY)
        assertEquals("v0.3", AppVersion.DISPLAY)
    }

    @Test
    fun aboutTypeScaleStaysInOneRegister() {
        assertTrue(aboutProductSp() <= 24f, "詰碁 must not be display-size in a dialog")
        assertTrue(aboutProductSp() > aboutSummarySp())
        assertTrue(aboutSummarySp() > aboutVersionSp())
        assertTrue(aboutVersionSp() >= 12f, "v0.3 must stay readable, not labelSmall")
        assertTrue(
            aboutProductSp() / aboutSummarySp() <= 1.6f,
            "product ${aboutProductSp()} vs summary ${aboutSummarySp()} jumps like displaySmall/bodyLarge",
        )
    }
}
