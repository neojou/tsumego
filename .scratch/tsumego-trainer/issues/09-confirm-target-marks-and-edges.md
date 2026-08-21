# 09: 確認畫面紅框標目標、真盤邊才粗線

**What to build:** Edit／確認畫面上，已標的目標棋串套紅框，左邊文字「目標: xx, xx, …」。真盤邊畫明顯粗黑線；牆不是盤邊，不畫粗黑帶（改切口虛線）。

**Blocked by:** 08: Edit 改現有題目

**Status:** resolved

- [x] 目標清單文案依座標排序
- [x] 紅框是套在子外的正方形
- [x] 只有真盤邊用粗外框線，牆不用

## Answer

`targetListLabel`、`targetFrameRect`、`drawsThickOuterLine`。確認畫面左欄列出目標；棋盤在子上畫紅框。牆改虛線，不再用深色寬帶冒充盤邊。