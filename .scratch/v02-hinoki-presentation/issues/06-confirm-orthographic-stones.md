# 06: 確認畫面正投影蛤碁石

**What to build:** 出題者在確認畫面（含 Edit 對圖）仍用原圖當底、不透視。子用與對局同一套蛤碁石石面、較薄正投影。目標細紅環；點擊仍打在印刷子上（15K／8K／small_trick 佈局測試仍過）。

**Blocked by:** 03: 檜木無縫紋與蛤碁石貼圖, 05: 目標環、最後一手點、短落／提子與兩聲

**Status:** resolved

- [x] 確認畫面原圖當底，沒有 12–18° 俯視扭圖
- [x] 子為正投影蛤碁石（同一套石面、較薄）
- [x] 目標細紅環，左邊「目標: …」仍在
- [x] overlay hit 仍對準印刷子；既有 15K／8K／small_trick 佈局測試過

## Answer

確認畫面 `BoardView` 有 overlay 時 `oblique=false`，同一套石面、正圓較薄。目標環與 overlay hit 不變。
