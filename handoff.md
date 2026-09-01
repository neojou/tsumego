# 詰碁 — browser Grok 交接

給**沒有本機 CLI、只在 browser 裡讀檔／討論**的 Grok。本機 CLI 已有 `Agents.md`、`CONTEXT.md`、`docs/decision.md`；這份把「先讀什麼、別踩什麼、怎麼開口」收成一頁。

產品現況：**v0.3**（About 顯示 `v0.3`）。KMP Compose Desktop + Wasm。使用者執黑，程式當白抵抗。v1 **只出殺棋**（標題「黑先殺白」）。

---

## 1. 這專案是什麼

圍棋**死活訓練機**，不是棋譜播放器、不是唯一正解測驗。

- 題目一律**黑先**；白先譜要先**翻色**再存。
- 過／不過看**終局**對**題型**的表（ADR-0003）。訓練機：任何仍能達成題型的著手都算過；程式回**最長抵抗**，不是預錄變化。
- v1 成功只認**無條件死**（淨殺）。無條件活、雙活、打劫（劫活／劫殺）都是**失敗**（白勝）。

詞表在 `CONTEXT.md`。輸出請用那份的詞，不要自創同義詞（例如不要把題目叫 puzzle、不要把應手叫最佳應手）。

---

## 2. 先讀這些（順序有用意）

| 順序 | 檔 | 為什麼 |
|---|---|---|
| 1 | `CONTEXT.md` | 領域語言：題目、題型、終局、應手、反駁手、成功／失敗 |
| 2 | `docs/decision.md` | **解題搜尋對照地圖**：現況怎麼搜、檔在哪、草圖、v1 沒做的。改搜尋／分類**先對這份與程式**，不要只對 ADR 標題 |
| 3 | `docs/adr/0003-goal-vs-outcome.md` | 題型 × 終局 過／不過 |
| 4 | `docs/adr/0024-v1-kill-only.md` | v1 只出殺棋 |
| 5 | `docs/adr/0012-five-modules.md` | 規則／分類／解題／題目庫／認圖 |
| 6 | `docs/adr/0014-classifier-order.md` | 終局分類順序 |
| 7 | `docs/adr/0011-longest-resistance.md` | 最長抵抗鍵 |
| 8 | `docs/adr/0009-ko-by-two-hypotheses.md` | 劫：贏劫／輸劫两次假設；即將成劫 |
| 9 | `docs/adr/0010-seki-static-definition.md` | 雙活；假眼不是共氣 |
| 10 | `docs/adr/0026-decision-tree-display.md` | 決策樹是**顯示契約**，不是搜尋剪枝 |
| 11 | `Agents.md` | CLI agent 技能入口（browser 可略過執行細節） |

其餘 ADR 按題再讀：`0002` 劫是終局、`0004` 同型反覆、`0015` 迭代加深（標題寫 α-β，**程式沒有 α／β 視窗**）、`0019` 證明完才下應手、`0023` 相關區。

樣例題目檔（對局 File → Samples 同源）：

- `docs/15K-kill.tsumego.json`、`docs/13K-kill.tsumego.json`、`docs/8K-kill.tsumego.json`、`docs/7K-kill.tsumego.json`（老鼠偷油）
- `docs/7K-kill-one-more-steps.tsumego.json`（丁四／one-more）
- `docs/small_trick.tsumego.json`
- `docs/9K-kill-20260828.tsumego.json`（提五子後做不成兩眼）

---

## 3. 程式從哪裡進

| 職責 | 檔 |
|---|---|
| 終局分類、做活點、劫、雙活、點眼、即將成劫 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/classify/Classify.kt` |
| AND–OR、最長抵抗、候選手、脫先捷徑 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/solve/Solver.kt` |
| 相關區、空手 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/solve/RelevanceZone.kt` |
| 對局：何時搜、應手落地、lastBlack、脫先 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/play/Session.kt` |
| 決策樹投影 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/play/DecisionTree.kt` |
| 規則／盤 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/board/` |
| 內建樣例 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/library/Samples.kt` |

測（改分類／搜尋時對這些，不要只跑「能編譯」）：

- 分類／對局：`Kill8BugLoopTest`、`Kill7BugLoopTest`、`Kill13BugLoopTest`、`DeadShapeTest`、`Kill9DeadShapeTest`
- 最長抵抗／樹（desktop，可能數十秒）：`SmallTrickSolverTest`、`Kill7MouseOilResistTest`、`Kill7SolverTreeTest`、`Kill8SolverTreeTest`、`Kill13SolverTreeTest`

座標：A–T 跳 I，A1 左下，19 在上。牆不給氣。

---

## 4. v1 契約（討論死活時先對這張）

決策樹**不自己判**劫／雙活／死。葉子只看 `classify`：

- `Goal.Kill.isSuccess` 只對 **無條件死** → 樹寫 **黑勝**，對局 **成功**
- 其它終局（無條件活、雙活、劫殺、劫活）→ 樹寫 **白勝**，對局 **失敗**
- 未定 → 繼續搜

因此「樹上過早白勝／失敗」幾乎都是**分類或候選手漏枝**，不是 AND–OR 或顯示收束的 bug。改顯示收束不會讓搜尋變快、也不會改死活。

`classify` 現況順序（ADR-0014 + 後來補的）：

提淨 → Benson 無條件活 → 雙活 → **假眼死形**（白一手能做成**與目標有關**的劫殺則改未定）→ 贏劫／輸劫 → 雙方已停則剩餘當死 → 做不成兩眼（同樣先看即將成劫）→ 未定。

---

## 5. 最近踩過、算法一開始就缺的（優先讀 `docs/decision.md` 對照表）

改分類／搜尋前請當**回歸清單**，不要為了快再加「黑永遠脫先」「氣區以外的打劫點不搜」這類捷徑。

| 現象 | 真正缺的 | 不要做的 |
|---|---|---|
| 13K 黑 R19 白 Q19 黑 S19 → 雙活／白勝 | 假眼不是共氣；真氣≤1 是點眼死形。氣洪水 P19→O19 會假裝還能活 | 把點眼塞進 `ownerCanForceLife`（會改 small_trick 應手 R19） |
| 8K 彎三 黑 S17 白 S19 黑 T18 → 雙活 | 做活不可提開局仍有兩氣以上的攻擊子（點眼不是眼內廢子） | 用彎三形狀表當捷徑 |
| 8K 黑 T18 白 S18 黑 T17 → 立刻黑勝 | 盤上還沒劫，但白 T19 做成單劫、黑只能 S19 提劫。v1 打劫＝失敗 | 因「劫還沒出現」就無條件死 |
| 8K 黑 T18 後白下 S19；樹 `S18–T17–S17→黑勝` | T19 **不是氣、不是 T17 鄰空**。分類知道即將成劫，搜尋沒把它當候選手 | 只改分類不改 `actions` |
| 7K 老鼠偷油 黑 T16 後白 Q15；P18 後 T18 白勝 | 脫先≠永遠脫先：T18 還要再填，黑 T15 立刻淨殺。應手 T15 是鄰空，不是節點比較多的 Q15 | 「做活點能活就 Refute」；節點多＝更頑強 |
| 7K one-more 樹沒有 `白 T17→黑 T18→黑勝` | T17 不是白氣（隔著黑 T16），只在 `lastBlack` 鄰空。樹測必須跟 Session 一樣傳 `lastBlack` | 以為丁四被凍成白勝 |
| small_trick 黑 S19 白 R19 黑 S15 | S15 脫先，T16 做活還差 S17；黑佔 S17 即淨殺。對局應繼續、白下 T16，不是立刻失敗。脫先時不要用「剛下之鄰空」（會選 S14） | 把脫先當成立刻反駁 |

`docs/decision.md` 裡「雙活／做不成兩眼／脫先／候選手」各節的對照表與草圖是 SSoT；改程式就改那份。

---

## 6. 在 browser 裡怎麼幫忙

Browser Grok **讀得到 repo 檔、改不到本機測試**時：

1. 先讀第 2 節的檔，再用領域詞重述問題（終局？應手？決策樹葉子？）。
2. 指出要動的函式與測檔，**不要**發明第二套「強」、不要改 ADR-0026 來修死活。
3. 若使用者貼決策樹／失敗畫面：對 `classify` 順序與 `actions` 候選手（氣、鄰空、做活點、**劫投入點** `whiteKoThrowIns`）。
4. 建議的 tight loop 寫成 `./gradlew :composeApp:desktopTest --tests …`（本機再跑）。長測在 `desktopTest`（wasm Mocha ~2s 會掐死）。
5. 正確死活優先於剪枝。相關區空手、RANK_WIN_PLY=3、脫先捷徑都曾把死活判錯。

本機 CLI Grok 可直接改檔、跑測、補 `docs/decision.md`。

---

## 7. 貼到 browser 的 prompt

下面整段複製即可。若 UI 能掛檔，再附上 `CONTEXT.md`、`docs/decision.md`、本 `handoff.md`。

```
你在協助開源專案「詰碁」（repo: tsumego）。Kotlin Multiplatform Compose，Desktop + Wasm。圍棋死活訓練機：使用者執黑，程式當白抵抗。現況 v0.3。

請先讀、且討論時遵守：
- CONTEXT.md：領域詞。題目／題型／終局／應手／反駁手／成功／失敗。不要用 puzzle、最佳應手、棋譜當題目。
- docs/decision.md：搜尋與分類的現況地圖。改解題前對這份與程式，不要只對 ADR 標題。
- handoff.md（本頁）或下面契約。
- docs/adr/0003、0024、0014、0011、0009、0010、0026。

契約：
- v1 只出殺棋（黑先殺白）。成功只認無條件死（淨殺）。無條件活、雙活、劫活、劫殺都是失敗（樹上白勝）。
- 決策樹只顯示 classify 的終局，不自己判死活。過早白勝／失敗 → 改分類或候選手，不是改顯示。
- 程式是迭代加深 AND–OR，沒有 α／β 視窗。Session 不設時限；證明完才落下應手。
- 最長抵抗鍵（現況）：winningBlack 少 → 非停 → proofPly 大 → 非脫先才比剛下之鄰空 → nodes 少 → 做活點 → 座標。脫先時若做活點仍讓黑能強迫，就下做活點。
- 候選手除氣區、做活點、lastBlack 鄰空外，還要有「一手會變成與目標有關的劫殺」的投入點（8K 的 T19）。

正確死活優先，不要為了快加捷徑。已知捷徑害過：假眼當雙活、做活提點眼、劫還沒出現就淨殺、T19 不在 actions、脫先當成永遠脫先、節點多當成更頑強。

樣例：docs/15K-kill、13K-kill、8K-kill、7K-kill（老鼠偷油）、7K-kill-one-more-steps、small_trick、9K-kill-20260828。測：Kill8BugLoopTest、Kill7MouseOilResistTest、SmallTrickSolverTest、Kill13BugLoopTest 及對應 SolverTreeTest。

請用繁體中文回答。先用領域詞確認問題，再指出檔案與測，不要先改顯示或發明新終局名。
```

若這次只要討論某一題，在 prompt 末尾加上例如：

```
這次只討論：8K 殺棋，黑 T18 後白不該下 S19；樹應走 白 S18 → 黑 T17 → 白 T19 打劫 → 白勝。
請先讀 Classify.kt 的 whiteKoThrowIns／whiteCanMakeKoKill，與 Solver.kt 的 actions／pickResist／pickRefute。
```

---

## 8. 本機 complementary

CLI 環境另見 `Agents.md`。議題在 `.scratch/<feature>/`（`docs/agents/issue-tracker.md`）。不要在 browser 討論裡發明第二套詞表；缺詞就標給 `/domain-modeling`，先不要寫進 UI。
