# 04: 搜尋路徑不列停；加速只用 RZS

**What to build:** 解題搜尋不把停當候選，盤右搜尋路徑不出現白停／黑停。加速只用事後判定區外落子為空手與必應區（論文 RZS），不用先停、不用蒙地卡羅猜點接入搜尋。使用者仍可按停。3×3 殺棋應手仍是 B3。

**Blocked by:** 01: 終局相關區與白的事後空手

**Status:** resolved

- [x] 對局搜尋路徑不含「停」
- [x] 解題候選手只有落子
- [x] 最長抵抗迴歸（B3）仍過

## Answer

`actions()` 只回落子、不含停。RZS 空手只判定區外落子。`AlphaBetaSolver` 不再呼叫蒙地卡羅，Session 不再傳開局白活點。使用者仍可按停。搜尋路徑測試 `searchPathsDoNotListPass`。
