# 08: Edit 改現有題目

**What to build:** TopMenu 為 Input、File、Edit、About。有題目時 Edit 可點，進入確認畫面改子、目標棋串、題型、四邊。確認後用新題目重新對局；取消回到原對局。殺棋多串目標必須全部死才成功；只提掉一串不算過。

**Blocked by:** 01: 從 small_trick.png 確認後產出題目檔

**Status:** resolved

- [x] 選單順序 Input File Edit About；無題目時 Edit 不可點
- [x] 題目可送進確認畫面再改目標／子／題型
- [x] small_trick 只提 R17/R18 不算殺成功（S16、T18 仍是目標）；若只標 R17/R18 串，提淨該串即成功

## Answer

`shellMenuBarItems` 加上 Edit。`Problem.toConfirmDraft()` 進確認畫面。殺棋 AND：`capturingR17R18LeavesS16AndT18SoKillIsNotYetSuccess`、`capturingTheOnlyMarkedWhiteStringIsImmediateSuccess`。