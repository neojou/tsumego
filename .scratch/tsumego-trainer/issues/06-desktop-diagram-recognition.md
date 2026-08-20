# 06: Desktop 認圖預填

**What to build:** Desktop 對齊平面圖在進確認畫面時自動填子（認圖 adapter）。預填後仍可在確認畫面改正。認圖失敗則退回 01 的空盤對圖擺子，不中止流程。Wasm 不做自動 CV，維持 01 行為。

**Blocked by:** 01: 從 small_trick.png 確認後產出題目檔

**Status:** resolved

- [x] Desktop 對 `small_trick.png` 這類對齊平面圖預填子後進入確認畫面
- [x] 預填錯誤可在確認畫面改掉再存成正確題目檔
- [x] 認圖失敗仍進入確認畫面，空盤＋原圖當底
- [x] Wasm 沒有自動填子，Input 流程與 01 相同
- [x] 自動測試只打認圖介面的固定草稿 adapter；不把 CV 準確率當 CI 門檻

## Answer

`DesktopDiagramReader` fills stones from the lattice. `EmptyDiagramReader` (Wasm) keeps the crop and image grid but leaves the 題目盤 empty. White First flip is `readDiagram`.
