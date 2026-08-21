# 02: 確認畫面與 Edit 鎖定殺棋

**What to build:** 出題者從 Import 或 Edit 進確認畫面時不能選題型，一律殺棋。目標必須是白才能確認。White First 翻色後仍是殺棋、標題將是黑先殺白；目標交叉點不變。拿做活譜進來只要改標白目標即可存成殺棋；目標是黑則不能確認。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 確認畫面沒有題型選單；草稿題型是殺棋
- [x] 目標為空或含黑 → 不能確認，說明須為白
- [x] 翻色只翻子色，題型仍殺棋，目標點不變
- [x] 標白目標後可確認，存檔為殺棋
- [x] Edit 現有殺棋題也不能改成其他題型

## Answer

確認畫面改靜態「題型：殺棋」，沒有選單。`ConfirmDraft` 預設殺棋，`toProblem()` / `readDiagram` 一律殺棋。目標非白走既有「殺棋／劫殺的目標必須是白」。翻色只翻子色。標白目標即可確認。
