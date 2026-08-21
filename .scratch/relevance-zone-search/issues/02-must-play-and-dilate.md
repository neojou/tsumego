# 02: 必應區聯集與最小 dilate

**What to build:** 黑找到過的一手時，相關區是 dilate(子區 ∪ 著手)。白全部擋不住時，相關區是 dilate(子區聯集)。dilate 把著手、整串、氣、一圈鄰點納入，倒撲／提子後證明仍對。雙活與劫終局相關區是整題目盤，不剪。證明失敗時，區外黑手同樣從必應區拿掉。

**Blocked by:** 01: 終局相關區與白的事後空手

**Status:** resolved

- [x] 提子／倒撲題剪枝後仍成功或仍出正確反駁手
- [x] 雙活／劫終局不因相關區被錯剪
- [x] 黑下在相關區外時，其餘區外黑手不必逐一搜完

## Answer

`dilate` 納入著手、整串、氣、一圈鄰點。OR 的 Yes 區是 dilate(子區∪著手)；AND 證完是 dilate(子區聯集)。雙活／劫終局 `terminalRelevanceZone` 回整題目盤。`orMoves` 對區外黑手同樣縮必應區。
