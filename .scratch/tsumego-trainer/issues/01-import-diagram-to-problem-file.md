# 01: 從 small_trick.png 確認後產出題目檔

**What to build:** 出題者用 Input → Black First 或 White First 選一張對齊的棋譜圖（以 `small_trick.png` 這張角題為例），進入確認畫面：原圖當底、點交叉點在空／黑／白間循環、標目標棋串（點一子可擴整串）、四邊切成真盤邊或牆、選題型。White First 先翻色再編。確認後才寫成題目檔。認圖失敗或 Wasm 從空盤對圖擺。這一張還不在主畫面落子、也沒有自動 CV。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Input 選單在 Desktop 與 Wasm 都有 Black First／White First，選圖後進入確認畫面
- [ ] 確認畫面以原圖為底，可改子、標目標棋串、設四邊、選五種題型之一
- [ ] White First 會翻色；目標交叉點不變；存檔後永遠黑先
- [ ] 雙活題沒有兩色目標、做活／劫活目標不是黑、殺棋／劫殺目標不是白時不能確認
- [ ] 確認後產出可開的 `.tsumego.json`（矩形、四邊、子、題型、目標棋串）；未確認不寫檔
- [ ] 以 `small_trick.png` 走完一輪：手動擺出角題並存檔
