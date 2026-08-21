# Import 流程兩邊都有；自動認圖仍只在 Desktop

`Import → Black First / White First` 在 Desktop 與 Wasm 都是：選棋譜圖 → 確認畫面（原圖當底、可改子、標目標、設四邊）→ 題目檔。選單名是 Import，不是 Input。v1 不選題型，一律殺棋（ADR-0024）。Desktop 可用認圖預填子；Wasm 與認圖失敗時手動對圖擺子。這取代 ADR-0006「Wasm 不能匯入圖」。認圖模組仍只在 desktopMain（ADR-0012），不是 commonMain CV。
