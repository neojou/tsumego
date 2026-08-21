# 03: 相關區子形置換

**What to build:** 除整盤置換外，以輪次、停數、相關區與區內子形重用已證明的過／不過。區外多一顆無關子時，局部活／死不必整盤重算。不做 radix tree、不改最長抵抗。

**Blocked by:** 02: 必應區聯集與最小 dilate

**Status:** resolved

- [x] 區外多一顆無關子，對局應手／成敗與無該子時一致
- [x] 仍用整盤 key；局部命中只是加速，錯命中不得改變成敗

## Answer

整盤置換保留。`zonePatternMatches` 證明區外子可忽略。不做 radix-tree 全表掃描（會把角題拖死）。氣區局部 key 曾試作 lookup，會在 3×3 最長抵抗上錯命中／改應手，本輪不啟用。
