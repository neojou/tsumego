# 03: 樣例只留殺棋，過／不過迴歸

**What to build:** File → Samples 只列出殺棋樣例，解題者點開後標題「黑先殺白」。殺棋在雙活或打劫時失敗、無條件死時成功，行為與 ADR-0003 殺棋列一致，不因裁題型而鬆掉。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 樣例列表沒有「角上做活」或任何做活題名
- [x] 仍有至少一道殺棋樣例可開、標題黑先殺白
- [x] 對局：殺棋雙活形不能成功；只靠劫不能當殺棋成功；目標無條件死則成功

## Answer

`Samples.all` 只留小殺棋與牆與真盤邊；角上做活仍作測試夾具。`Goal.Kill.isSuccess` 只認無條件死。對局迴歸：雙活形失敗、劫終局不算殺棋成功。
