# 01: 非殺棋題目檔拒絕載入

**What to build:** 解題者打開題目檔時，題型必須是殺棋才能進入對局，標題為「黑先殺白」。做活、雙活題、劫活、劫殺的檔被拒絕並說明。新存的檔題型仍是 kill。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 字面 JSON 的 goal 不是 kill → 載入失敗，說明與題型有關
- [x] goal 為 kill 且目標為白 → 可開，對局標題「黑先殺白」
- [x] 寫出再讀回，題型仍是殺棋
- [x] Desktop 與 Wasm 同一份非法檔都被拒（common 行為）

## Answer

`ProblemLibrary.decode` 在 `parseGoal` 之後若不是殺棋就回 `ProblemLoad.Err("v1 題型只開殺棋")`。`encode` 仍寫 `goal: kill`。對局標題走 `Goal.Kill.playHeading()` →「黑先殺白」。行為在 commonMain，Desktop 與 Wasm 同一份檔。
