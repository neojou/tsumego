# 02: 盤桌、牆外切口、真盤邊線

**What to build:** 解題者打開樣例，題目盤浮在盤桌上（淺麻或暗桌）。牆外不是可下的木、帶切口暗邊。真盤邊粗黑線，牆虛線。點交叉點仍落到同一點。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 檜木盤區域與盤桌分開；牆外側不是木紋
- [x] 真盤邊用粗線，牆用虛線並有切口暗邊
- [x] 交叉點 hit 與改繪前同一點（15K／13K／8K 與既有佈局測試）
- [x] 牆外不可下，與規則一致

## Answer

`woodRect` 只在真盤邊外留木緣，牆邊木止於格線。畫布底為盤桌色，牆外切口暗帶。hit 仍走 `boardLayout`。
