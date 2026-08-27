# 解題搜尋對照地圖

現況怎麼搜、檔在哪、草圖是什麼、哪些文獻手法 v1 **沒做**。詞在 `CONTEXT.md`。為什麼在 `docs/adr/`。改搜尋前先對這份與程式，不要只對 ADR 標題。

殼怎麼列決策樹見 ADR-0026；那是顯示契約，不是搜尋剪枝。

---

## 模組

| 職責 | 檔 | 重點 |
|---|---|---|
| 解題入口、AND–OR、最長抵抗、報路徑 | `composeApp/src/commonMain/kotlin/com/neojou/tsumego/solve/Solver.kt` | `AlphaBetaSolver`、`Search.canForce`、`pickResist`／`pickRefute`、`formatSearchPath` |
| 相關區、空手、必應區、區形 | `…/solve/RelevanceZone.kt` | `terminalRelevanceZone`、`dilate`、`isNullMove`、`retainMustPlay`、`zonePattern` |
| 蒙地卡羅猜順序 | `…/solve/MonteCarlo.kt` | **有程式，未接入** `AlphaBetaSolver`／`Session` |
| 終局分類、做活點 | `…/classify/Classify.kt` | `classify`、`firstOwnerMoveToTwoEyes`、`ownerCanForceLife`、`isAwayFromTargets` |
| 對局：何時搜、路徑串流、應手落地 | `…/play/Session.kt` | `launchSearch`、`onPath`、`replyCache`、`DISPLAYED_SEARCH_PATHS` |
| 殼：決策樹 | `…/play/PlayCopy.kt`、`…/play/DecisionTree.kt`、`…/Tsumego.kt` | 標題決策樹、三層縮排、不編號 |

介面：`Solver.solve(SolverInput) → SolverResult`（`Resist`＝應手、`Refute`＝反駁手、`Timeout`）。Session 一律 `UnlimitedBudget`。換算法不改 ADR-0012 五塊切法。

---

## 何謂盤上「強」的白棋

**應手**只指落下的那手。選哪手叫 **最長抵抗**（ADR-0011）。樹裡每一手白棋也用同一套鍵（顯示收束用），不要第二套「強」。

`Search.pickResist`：在仍讓黑能強迫的白棋著手裡取 `resistOrder` 最小：

1. `winningBlack` 少（`countWinningBlackReplies`：黑有幾手仍能強迫）
2. 非停優於停
3. `proofPly` 大（黑最短勝枝還要多少 ply）
4. `nodes` 多
5. **做活點**（`firstOwnerMoveToTwoEyes`）
6. 座標小：`file * 20 + rank`（A1 < A2 < B1）；停最後

`pickRefute`（黑已不能強迫）：做活點若在反駁集合裡就下它；否則 `refuteLifeTier`（已無條件活 → 其它終局失敗 → 仍能做成活 → 其它），再比離兩眼還幾手，再座標。

脫先（`blackPlayedAway`）：若做活點讓白能強迫活，不跑迭代加深，直接 `Refute` 並報一條路徑。

UI「從路徑中思考最強應手...」＝證明完、尚未落下應手（`pickingReply`），不是樹裡的 C。

---

## 決策樹用哪個方法（現況）

ADR-0015 標題寫 α-β／negamax。程式是：

- 二元問題：黑能否強迫題型過
- 迭代加深 `depth = 1..48`，第一次得到 `Force.Yes` 或 `Force.No` 就停
- 黑 **OR**（`orMoves`）、白 **AND**（`andMoves`）
- 葉子：`classify`（ADR-0014）
- **沒有** α／β 視窗、沒有 negamax 分數

```
solve(position):                          # 輪白，黑剛下完
  if 脫先 and 做活點能活: Refute(做活點)
  for d in 1..48:
    r = canForce(pos, White, passes, d, [])
    if r is Yes: return pickResist(d)
    if r is No:  return pickRefute(d)
  Timeout

canForce(pos, toPlay, passes, depth, path):
  yield every 8 nodes
  o = classify(pos)
  if 題型過:     emit 搜尋路徑; return Yes(ply=0, zone=terminalZone)
  if 終局 or 雙方已停: emit 搜尋路徑; return No(zone=terminalZone)
  if TT hit: return TT
  if depth == 0: return Unknown
  if Black: r = orMoves(...)   # 任一 Yes 即可；保留最短 proofPly
  if White: r = andMoves(...)  # 任一 No 即反證；否則 Yes 取最壞 ply
  store Yes/No in TT (zone stripped)
  return r

orMoves:  # 黑
  for each action in pending:
    child = canForce(..., Black's move)
    Yes → 記最短 ply；ply<=1 可立刻 return
    No  → 若是空手: pending = 必應區未搜過的點（停拿掉）
  有 Yes → 那條；全 No → No

andMoves:  # 白
  for each action in pending:
    child = canForce(..., White's move)
    No  → 立刻 return No          # 一個反證夠
    Yes → 若是空手: pending = 必應區
  全 Yes → Yes(worstPly)
```

候選手 `actions`：氣區空點（`relevantEmptyPoints`，不是相關區）、根上做活點、立刻讓黑無法過的白棋、提子、夾氣、氣、最後停。`guess` 參數現在沒人傳。

路徑：`formatSearchPath` 結尾是黑勝／白勝。含停的路徑不報。Session 串流 `onPath`（計走完條數）與 `onPv`（決策樹葉子）；證明完 `onPathsComplete` 才 `pickingReply`。

---

## 空間剪枝（相關區）— 有做

Shih 風格 RZS，在 `orMoves`／`andMoves` **事後**收縮，不是預先裁死一塊盤。

| 概念 | 函式 | 行為 |
|---|---|---|
| 終局相關區 | `terminalRelevanceZone` | 無條件活：Benson 活串＋氣。無條件死：目標＋鄰＋氣（雙方已停再加鄰串）。雙活／劫／未定：整塊可下 |
| 長大 | `dilate` | 剛下的點、整串、氣、鄰 |
| 空手 | `isNullMove` | `Move` 且點不在子區；**停不是空手** |
| 必應區 | `retainMustPlay` | 尚未搜、且在區內的空點；看到空手後清掉區外剩餘（含停） |

白 AND 第一個 `No` 就返回。黑 OR 在 `proofPly <= 1` 的 `Yes` 可早退。

`relevantEmptyPoints` 只是候選手池（目標氣＋夾氣空點），不要和相關區搞混。

ADR-0023。不移植 CGI／FTL。

---

## 深度縮短（FTL）— 沒做

沒有 Failure-to-Life／CGI 短勝枝搜尋。證明深度是 AND–OR 的 `proofPly`（黑 OR 取最短勝枝、白 AND 取最壞），用來排最長抵抗，不是 FTL 演算法。ADR-0023 寫明不移植。

`Force.Yes` 且無法做成無條件活的無條件死會提早當葉子，填氣不灌證明深度（ADR-0014）。

---

## 重用加速 — 部分做

| 機制 | 狀態 | 位置 |
|---|---|---|
| 同一次 `solve()` 局面置換表 | **有** | `Search.proven`、`outcomes`。鍵：`position.key\|toPlay\|passes\|superko`。存 Yes／No＋ply／nodes，**剝掉 zone** |
| 區形模式表 | **有型別、搜尋沒用** | `ZonePattern`／`zonePattern`／`zonePatternMatches`；`RelevanceZoneTest` |
| 跨一手應手快取 | **有** | `Session.replyCache`：同一題同一局面再下同一手不重搜。重做清畫面、留快取 |
| 全庫／跨題置換 | **沒有** | |

```
ttKey = pos.key + toPlay + passes + sorted(superko)
on proven Yes/No: proven[ttKey] = stripZone(result)
on enter: if proven[ttKey] return it
```

---

## 搜尋導向改良 — 沒做

| 手法 | v1 |
|---|---|
| df-pn／PN-search | 沒有。ADR-0015 明確不問 |
| EWS | 沒有 |
| 專門化 MCTS（樹） | 沒有 |
| 隨機模擬猜順序 | `MonteCarlo.kt` 有 `rankMovesByPlayout`／`findOpeningWhiteLife`；**解題路徑已拔掉**（ADR-0022／0023） |

以後要換引擎：仍走 `Solver` 介面，葉子仍 `classify`，應手仍最長抵抗。不要在殼另做一套「強」。

---

## 決策樹顯示

契約 ADR-0026。搜尋仍報每一條搜尋路徑；殼投影成樹。

```
白下 A1
  黑下 B11
    白下 C111 -> 黑下 D -> 黑勝     # C 起是該節點最長抵抗主線
  黑下 B12
    白下 C112 -> 白勝
白下 A2
  黑下 B21
    白下 C221 -> 黑下 D -> 白勝
```

- 白 A、黑 B：搜尋裡出現過的都列；區塊依第一次出現；新黑掛在該白最後
- 已有的 (A, B) **只改寫第三層文字**，不因 C 被取代而搬家
- 新白 A 加在樹最底
- 不編號。`搜尋路徑數目： <走完條數>（列出 <葉子數>）`；走完不封頂；葉子最多 1000
- 對局成功／失敗不變；思考中仍「從路徑中思考最強應手...」

殼標題「決策樹」，三層縮排、不編號。解題器 `onPath` 計條數，`onPv` 更新每個 (A, B) 目前最長抵抗續線（搜哪些枝不變）。

---

## 改搜尋時從哪裡下手

1. 證明對不對：`canForce`／分類／相關區；測 `SessionSolverTest`、`SmallTrickSolverTest`、`Kill8BugLoopTest`
2. 應手穩不穩：`resistOrder`、`pickRefute`；測最長抵抗與做活點
3. 要更快：先量 `onPath` 條數與節點，再考慮接上 `zonePattern`、加深 TT、或新 `Solver`（df-pn 等）。顯示收束不會讓搜尋變快
