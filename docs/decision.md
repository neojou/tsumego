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
| 終局分類、做活點、劫、雙活 | `…/classify/Classify.kt` | `classify`、`isSeki`、`deadlockSeki`、`isFalseEyeDead`、`classifyKo`、`ownerCanForceLife`、`minOwnerMovesToTwoEyes`（不做活提子）、`firstOwnerMoveToTwoEyes` |
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
4. **黑剛下之鄰空**（僅非脫先。7K 老鼠偷油黑 T16 後 T15，不是 Q15。脫先時 S15 的鄰空 S14 不是應手，應做活點 T16）
5. `nodes` **少**（證明灌水不是更頑強；Q15 節點多仍是邊上敗著）
6. **做活點**（`firstOwnerMoveToTwoEyes`）
7. 座標小：`file * 20 + rank`（A1 < A2 < B1）；停最後

`pickRefute`（黑已不能強迫）：做活點若在反駁集合裡就下它；否則 `refuteLifeTier`（已無條件活 → 其它終局失敗 → 仍能做成活 → 其它），再比離兩眼還幾手，再座標。

脫先（`blackPlayedAway`）：做活點**這手後已 Benson**，或黑回來一手殺不掉且白仍能連填兩眼，才直接 `Refute`。不可把「黑永遠脫先、白連填到 Benson」當反駁——7K 黑 P18 後 liveAt=T18、`canLive=true`，但黑 T15 立刻淨殺。small_trick 黑 S19 白 R19 黑 S15：liveAt=T16 還要再填 S17，黑 S17 立刻無條件死，故 T16 是應手不是立刻失敗；黑 S13 後 S19 黑一手殺不掉，仍可反駁。

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
  if 脫先 and 做活點:
    next = 白下做活點
    if 已 Benson or (仍能連填兩眼 and 黑一手殺不掉): Refute(做活點)
    # 否則搜尋：P18 後 T18 會被 T15 淨殺
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
  # 終局含劫殺、雙活、點眼死形、做不成兩眼的無條件死。v1 殺棋只認無條件死為過。
  # 分類必須：旁劫不要凍成劫殺；兩串白棋的氣不要加總成假雙活；
  # 假眼不當共氣（點眼不是雙活）；做活不可提兩氣以上攻擊子（彎三點眼是對殺，不是活）。
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

候選手 `actions`：氣區空點（`relevantEmptyPoints`，不是相關區）、根上做活點、**黑剛下之鄰空**（`SolverInput.lastBlack`；7K 老鼠偷油黑 T15 後的 T16、one-more 黑 S17 後的 T17 都不在氣區裡——T17 與白 T15 中間隔著黑 T16，只靠鄰空才進搜尋）、**貼目標黑子的打劫點**（8K 黑 T18 白 S18 黑 T17 後的 T19 不是氣、不是 T17 鄰空，不搜就只剩 S17→黑勝，最長抵抗會變成 S19）、立刻讓黑無法過的白棋、提子、夾氣、氣、最後停。`guess` 參數現在沒人傳。Session `launchSearch` 一定帶 lastBlack。決策樹測必須傳，否則 T17–T18 丁四那枝根本不會出現（誤以為凍住）。測：`Kill7SolverTreeTest.afterS17TreeMustNotCallT19WhiteWin`、`Kill7BugLoopTest.afterS17WhiteT17IsInActionsAndT18NotWhiteWin`、`Kill8BugLoopTest.afterT18WhiteResistsAtS18NotS19`。

路徑：`formatSearchPath` 結尾是黑勝／白勝。含停的路徑不報。Session 串流 `onPath`（計走完條數）與 `onPv`（決策樹葉子）；證明完 `onPathsComplete` 才 `pickingReply`。

---

## 路徑已出、應手未下（現況）

這不是顯示漏掉應手。ADR-0019：證明之前不下子。決策樹在**證明途中**就長，應手在**證明之後還要再選一次**才落地。殼「輪白, 思考時間」整段都在走；`pickingReply` 之後多一行「從路徑中思考最強應手...」。

```
黑落下 → Session.launchSearch（UnlimitedBudget）
  canForce 迭代加深 1..48
    走到終局 → onPath / onPv          # 決策樹開始有葉子；白子還不在盤上
    白根 AND 要 Yes：必須把所有白棋都證完（任一 No 才早退成反證）
  第一次 Yes 或 No
    onPathsComplete → pickingReply=true
    Yes → pickResist(proveDepth)      # 再掃根上每一手白棋
    No  → pickRefute(proveDepth)
  Resist / Refute 才 applyWhite
```

同一份 `Search`（TT 還在）。`pickResist` 對根上每一手白棋的「能否強迫」走 TT（證明輪已寫入，通常是微秒）；`countWinningBlackReplies` 只數 **已證明的黑勝手**：TT 命中直接算，未證明的黑手最多再搜 `RANK_WIN_PLY`（3）ply。不可用滿 `proveDepth-1` 重證——7K 老鼠偷油黑 T16 後證明 7s、1968 條路徑就 `pickingReply`，接著對 9 手白棋在深度 7 數黑手，單枝 Q15 後 T17 要 20s，UnlimitedBudget 會再想幾分鐘、路徑數不再增加。

```
pickResist(d):
  for white in moves(根, White):          # 做活點、立刻反證、提子、夾氣、氣、Pass
    for k in 1..d:
      child = canForce(白下完, Black, k, record=true)
      Yes → 記住; break
      No  → 這手丟掉（不是應手候選）
    if Yes: winningBlack = countWinningBlackReplies(白下完, d)
  在仍 Yes 的白棋裡取 resistOrder 最小 → Resist

countWinningBlackReplies:
  對 actions(黑) 每一手：
    TT 已有 Yes／No → 用它
    否則 canForce(黑下完, White, min(d-1, 3), record=false)
  數 Yes 的個數
```

`resistPv`（顯示用最長抵抗續線）**只在 `path.size == 2`**（已有白1、黑1，輪到下一手白）對該節點的 `yesKids` 算 `countWinningBlackReplies`。根（`path.size == 0`）的 PV 只用「最壞 proofPly」，不當最長抵抗，所以根應手一定走 `pickResist` 第二輪。

測：`Kill7ReplyLagTest.afterS17PathsAppearBeforeReply`、`mouseOilAfterT16MustLandReplyAfterPicking`。7K 黑 S17 後桌面一輪：第一條路徑 37ms、證明完 715ms、應手落地 1.64s。老鼠偷油黑 T16：證明完 7.3s、1968 條路徑後必須落下應手，不能在 `pickingReply` 再滿深度數黑手。

### 7K 為什麼特別容易卡在「有樹、沒應手」

1. 白根要證明黑仍能強迫 → AND 窮舉所有白棋。每搜完一手，終局路徑就先噴出來；剩下的白棋還沒證完，不能落下應手。
2. 劫分類修正後（見下節）：旁劫不再當葉子，T17/T18/T19 那條繼續展開，證明變深、路徑變多。
3. 有單劫的未定局面，`classify` 每次都可能跑 `ownerCanForceLife`（目標色連下到兩眼，上限 4000 節點）。
4. 證明完 `pickResist` 若用滿深度重數黑手，單枝可到 20s（T16 後白 Q15、黑 T17）；現況未證明的黑手只搜 3 ply，TT 已有的 Yes／No 直接算。
5. Session 不設時限。算不完就一直「思考時間」往上加，悔棋才中止。

這是搜尋政策＋最長抵抗的成本，不是決策樹投影的 bug。顯示收束不會讓這段變快。

### 對照「還沒做／少做」的缺口

| 缺口 | 現況 | 對 7K 的影響 |
|---|---|---|
| 根 Yes 的 `yesKids` 丟棄 | 強迫結果走 TT；數黑手未證明只 3 ply | T16 後不再因滿深度數黑手卡幾分鐘 |
| 最長抵抗只在 `path.size==2` 算 | 根 PV 用最壞 ply | 根應手不能從證明那輪直接取 |
| 證明中途就下目前最好的白棋 | ADR-0019 禁止 | 有路徑 ≠ 有應手 |
| 時限 | ADR-0016 已廢，改 ADR-0019 | Wasm 可想好幾分鐘 |
| 區形模式表 | 有型別、搜尋沒用 | 同形重證 |
| df-pn／PN | 沒有（ADR-0015） | 不會把節點集中在未證明的白棋 |

再縮「有樹沒子」：讓 `pickResist` 直接吃證明輪 `yesKids` 的 `winningBlack`，不必再掃根上白棋。不要為了快而改最長抵抗的鍵。

---

## 劫殺：怎麼判定、決策樹怎麼當葉子

決策樹**不自己判斷劫殺**。葉子只看 `classify` 回的終局，再對題型表（ADR-0003）：

```
o = classify(pos)
if goal.isSuccess(o):     黑勝 / Force.Yes     # 殺棋只認無條件死
else if o 已是終局:       白勝 / Force.No      # 含劫殺、無條件活、雙活、劫活
else:                     繼續展開
```

v1 殺棋：劫殺 **不是過**（`Goal.Kill.isSuccess(KoKill) == false`）。因此一旦分類成劫殺，搜尋當葉子、決策樹寫 **白勝**、對局是 **失敗**。這不是顯示 bug；若葉子不該出現，要改的是分類（ADR-0002、0009、0014），不是 AND–OR。

### 何時才叫劫殺

牆上沒有劫材，不當成對局去找劫材（ADR-0002）。分類用 **贏劫／輸劫** 两次假設（ADR-0009），不在樹上演劫鬥。

入口：`classify` 在 Benson、雙活之後，若 `hasKoCandidate()` 才進 `classifyKo`。

```
hasKoCandidate:           盤上有「一子一氣」的串（可能還不是可提的單劫）
simpleKoCaptures(toPlay): 對方一子一氣，提點提完後己方也是一子一氣 → 真單劫提

classifyKo(pos, targets):
  blackKos = simpleKoCaptures(Black)
  whiteKos = simpleKoCaptures(White)
  if both empty: return null          # 只有一子一氣、做不成單劫提 → 不當劫

  winBlack = resolveKos(pos, Black)   # 假設黑贏所有單劫（最多 8 步）
  winWhite = resolveKos(pos, White)
  blackLife = basicLife(winBlack)     # Benson 活 / 雙活 / 其它；提淨算死
  whiteLife = basicLife(winWhite)

  目標只剩黑:
    黑贏劫能活、白贏劫不能活 → 劫活
    兩邊假設都能活 → 無條件活
  目標只剩白:
    兩邊假設都能 Benson 活 → 無條件活
    目標已提淨 → 無條件死
    單劫提、除劫點外提不到目標 → 劫殺   # 本題劫
    做不成兩眼且劫仍在 → 劫殺           # 8K 提劫
    白一手能做成上述劫殺 → 未定         # 8K T17，不可先淨殺
    否則 null                           # 旁劫（R19）
  雙方已停且两次假設都是雙活 → 雙活
  其它 → null
```

`resolveKos(winner)`：有己方可提的單劫就提（忽略同型反覆）；否則對方可提時，用 `fillKoForWinner` 當成己方已佔劫、拔掉對方那一子。`basicLife` **不再走** `classifyKo`，只看提淨／Benson／雙活。

### 和決策樹的接縫（7K 的坑）

殺棋目標是白。舊規則「盤上有單劫且白目標還在 → 一律劫殺」會把 **旁劫** 當成整題終局：7K 黑 S17 後白 T19，T17/T18 是單劫，但 S18–S19–T19 只剩氣 R19，黑提三子與那劫無關。分類成劫殺 → `Force.No` → 樹上 `白下 T17 -> 黑下 T18 -> 白下 T19 -> 白勝`。

現況：白目標 **還能做活** 則 `classifyKo` 回 `null`，局面未定，R19 會被展開。白目標 **做不成兩眼** 且單劫仍在才劫殺（8K 提劫那條：淨殺才過，打劫失敗）。測：`Kill7BugLoopTest`、`DeadShapeTest.kill8KoTakeIsKoKillNotUnconditionalDead`。

---

## 雙活：怎麼判定、決策樹怎麼當葉子

決策樹**不自己判斷雙活**。葉子只看 `classify`：`Outcome.Seki` 對殺棋不是過 → `Force.No` → 樹寫 **白勝** → 對局 **失敗**。若那枝其實還能淨殺，要改的是分類（ADR-0010、0014），不是 AND–OR 或顯示收束。

`classify` 順序（ADR-0014）：提淨 → Benson 無條件活 → **雙活** → **假眼死形** → 劫 → 雙方已停則剩餘當死 → 做不成兩眼則無條件死 → 未定。雙活在「做不成活則死」之前，所以假雙活會擋住丁四／詰氣。點眼死形也必須在做活洪水之前：13K S19 的 P19 往 O19 灌空點 > 8，第 (6) 步會當成還能活。

詞：CONTEXT「雙活」＝雙方靠共氣共存，不是無條件活。ADR-0010：不是把「未定」或「雙方已停」直接當雙活。

### 兩條路徑

`isSeki` 在 `Classify.kt`。v1 殺棋目標只有白，黑鄰串不在 `targets`，所以要兩條：

```
isSeki(pos, targets, bothPassed):
  # 路徑 A — ADR-0010：targets 裡有黑也有白（雙活題／雙方已停）
  if bothPassed and 黑目標、白目標都非空 and 都非 Benson:
    if 黑氣 ∩ 白氣 非空: return true

  # 路徑 B — 殺棋：不等停、不要求黑在 targets
  return deadlockSeki(pos, 白目標)
```

路徑 A 殺棋 v1 **走不到**（沒有黑目標）。若不做路徑 B：未停 → 未定繼續下；雙方已停 → 「剩餘當死」→ 無條件死 → 假 **黑勝**。測：`SessionOutcomeTest.killFailsOnSekiShape`（路徑 A，targets 含黑白）。

### 路徑 B：`deadlockSeki`（現況草圖）

靜態條件，不搜白應手。黑詰氣是「攻擊方連續填自己不入氣的氣」。

```
deadlockSeki(pos, whiteTargets):
  if 白目標空 or 任一 Benson: return false
  if 任一方有單劫提: return false          # 單劫交給 classifyKo
  strings = 白目標在盤上的棋串（按串，不把氣加總）
  if 任一串 attackerCanCapture: return false               # 還能安全詰到提掉

  whiteLibs = 所有白目標串的氣的聯集
  adjBlack  = 那些氣旁邊的黑子
  if adjBlack 空 or 任一 Benson: return false
  shared = whiteLibs ∩ adjBlack的氣
  if whiteLibs 不全是共氣: return false
  if shared 點數不在 2..4: return false
  if 任一共氣點 fillCapturesEither: return false           # 填下去立刻提掉對方
  realDame = shared 裡不是假眼的點
  if realDame ≤ 1: return FalseEyeDead                     # 點眼（13K S19），不是雙活
  if !ownerCanForceLife: return Neither                    # 做不成兩眼不是雙活（8K 白S19黑T18）
  return Seki
```

`classify`：`Seki` → 雙活；`FalseEyeDead` → 無條件死。不要把點眼塞進 `ownerCanForceLife`（見下節）。

```
attackerCanCapture(pos, stones, depth≤6):
  remaining = 這串還在盤上的子
  if remaining 空: return true          # 已被提
  for p in remaining 的氣:
    next = 黑下 p
    if 這串被提掉: return true
    if 黑這手後自己 < 2 氣: skip        # 入氣／倒撲不當安全詰氣
    if attackerCanCapture(next, remaining): return true
  return false                          # 安全詰氣提不掉
```

```
fillCapturesEither(p):
  黑下 p 提掉任一白目標 → true
  白下 p 提掉任一鄰近黑 → true
```

`ownerCanForceLife`：目標色連下能否 Benson（眼位空點 > 8 直接當還能活）。封死的單眼／兩眼空點走這條。**點眼死形不走這條**：2–4 口全共氣且真氣≤1 由 `isFalseEyeDead` 在雙活之後立刻無條件死。

### 對照題

| 局面 | 為什麼 | 終局 |
|---|---|---|
| 老鼠偷油 T18–S17–T16–T15–T17 | 白**一串**，氣 Q15/R19/S16/T19 全共氣。Q15／R19 安全詰完仍剩 S16／T19 入氣，`canLive=true` | 雙活 → 殺棋白勝。測 `Kill7MouseOilResistTest.t18S17T16T15T17IsSekiKillFailure` |
| 老鼠偷油 T16–Q15–P15–P14–P18 | P18 脫先，liveAt=T18，`canLive=true`。舊捷徑直接 `Refute(T18)` → 樹上白勝／失敗。黑 T15 立刻無條件死 | **必須搜尋**。脫先只在黑一手殺不掉時才反駁。黑 T16 後應手 T15（鄰空），不是 Q15。測 `afterT16Q15P15P14P18T18T15IsKillSuccessNotWhiteWin`、`afterP18AwayMustNotRefuteAtT18`、`afterT16WhiteResistsAtT15` |
| one-more S17–T17–T18 | T17 不是白氣（T15 與 T17 隔著黑 T16），只在 lastBlack 鄰空裡。T18 **提掉 T17**，S17 可連 T16／T18 成丁四。白裂成 S18–S19 與 T15 下邊**兩串**。S18–S19 氣只有 R19／T19，R19 安全再 T19 提掉 | **未定**，不是雙活。樹不可在 `白下 T17 -> 黑下 T18 -> 白勝` 停，且必須出現 `… -> 黑勝`。測 `Kill7BugLoopTest.afterS17T17T18IsUnsettledNotSeki`、`Kill7SolverTreeTest` |
| small_trick S19–S17–T16–S18 | 4 共氣、填了當下提不到，但黑 S15 後 `canLive=false` | 詰氣／無條件死，不是雙活。測 `DeadShapeTest.smallTrickConnectThenT16S18IsUnsettledNotSeki` |
| 13K R19–Q19–S19 | 一串 3 氣 P19/R18/T19 全共氣；R18、T19 是假眼，真氣只剩 P19。P19 安全詰氣，R18／T19 是入氣 | **點眼死形** → 無條件死／黑勝，不是雙活、不是未定。樹上不可 `黑下 S19 -> 白勝`。測 `Kill13BugLoopTest`、`Kill13SolverTreeTest` |
| 8K S18–S19–T18 | 3 口全共氣（S17/T17/T19），T19 邊上看起來像假眼，S17–T17 是兩口眼位。`canLive=false` | **不是雙活**。誤判雙活會讓 T18 不算黑勝手，S19 的 proofPly 變長，最長抵抗選 S19 而不是做活點 T18。測 `Kill8BugLoopTest.afterS18S19T18IsDeadNotSeki`、`afterS18WhiteResistsAtT18NotS17` |
| 8K S17–S19–T18 | 氣 S18/T17/T19 全共氣。T18 點眼做成彎三。舊做活 BFS 填三口提掉仍有三氣的 T18 才 Benson，`canLive=true` → 雙活 | **彎三死形** → 無條件死／黑勝。樹上不可 `白下 S19 -> 黑下 T18 -> 白勝`。測 `Kill8BugLoopTest.afterS17S19T18IsKillSuccessNotWhiteWin`、`Kill8SolverTreeTest` |
| 8K T18–S18–T17 | 盤上還沒劫、S17 像假眼、`canLive=false`。白 T19 做成單劫，黑只能 S19 提劫。T19 不是氣、不是 T17 鄰空 | **未定**，不是淨殺。搜尋必須含 T19，否則只搜 S17→黑勝，根上誤選 S19。最長抵抗：S18–T17–T19 打劫白勝。測 `afterT18S18T17IsKoFightNotKillSuccess`、`afterT18WhiteResistsAtS18NotS19` |

舊錯：把所有白目標的氣**加總**成一組 2–4 共氣。one-more T18 後 Q15+R19+S16+T19 長得像老鼠偷油，其實是兩串。

### 和決策樹的接縫

殺棋：`Seki` → 不能強迫無條件死 → 該枝 **白勝**，對局失敗，不必再輪黑（與劫殺相同）。假雙活的症狀就是樹上過早出現 `… -> 白勝`，對局被宣判失敗。

還沒做／刻意不做：

- 沒有丁四／盤角曲四形狀表；能殺就靠「按串詰氣」、點眼死形、或「詰完做不成兩眼」落到未定／無條件死，再交給 AND–OR
- `attackerCanCapture` 不模擬白應手（攻擊方連填）
- 入氣的填不當安全詰氣（倒撲／棄子要靠後續搜尋，不在分類裡演）
- 路徑 A 仍要雙方已停且 targets 含黑白；殺棋只靠路徑 B
- 一開始的 `deadlockSeki` **沒有**「假眼 ≠ 共氣」：2–4 口全共氣 + 安全詰氣提不掉 = 雙活。13K 點眼長得像老鼠偷油（剩氣全共、詰 R18／T19 會入氣），會凍成雙活／白勝。這是分類缺口，不是 AND–OR。
- 做不成兩眼也不是雙活。8K 白 S19 黑 T18 三口全共、`canLive=false`：當成雙活後 T18 不是黑勝手，S19 的證明變深，最長抵抗選 S19 而不是做活點 T18。
- 做活不可吃點眼。8K 黑 S17 白 S19 黑 T18 彎三：填三口提掉仍有三氣的 T18 才 Benson，是對殺。舊規則只禁一次提兩顆，`canLive=true` → 雙活 → 樹上白勝。

---

## 做不成兩眼則死（現況）

這就是「黑脫先／當停，白連續落子仍做不成兩眼 → 無條件死 → 殺棋黑勝」。**不是**缺規則。ADR-0014 第 (6) 步、`ownerCanForceLife`。Session `applyBlack`：`classify` 已是無條件死就 **Success**，不 `launchSearch`。

「黑 pass 一手」只有 1 ply，不夠當無條件死。現況是攻擊方**一直**脫先，目標色連填眼位，看能否 Benson。

```
ownerCanForceLife(pos, targets):
  onBoard = 還在盤上的目標
  if 空: return false
  if 已提掉一部分目標:
    if 局部眼位（氣＋再一圈）> 8: return true
  else:
    if 從氣灌出的空點 > 8: return true   # 13K S19 的 P19→O19 會走這裡，所以點眼不能靠這條
  return minOwnerMovesToTwoEyes != null

minOwnerMovesToTwoEyes:                      # BFS，最多 8 手、4000 節點
  目標只剩一色
  空點只從眼位（目標氣＋再一圈空點，最多 8 點）
  目標色連下
  一次提掉兩顆以上對方子 → 不算（對殺）
  提掉開局時還有兩氣以上的攻擊子 → 不算（點眼不是眼內廢子；8K 彎三 T18）
  開局已是一氣的單顆廢子仍可提
  全部剩餘目標都 Benson → 還能活
  否則 null → 做不成兩眼
```

`classify` 在雙活、**假眼死形**、劫之後：`!ownerCanForceLife` → 若白一手能走到劫殺則**未定**，否則 `UnconditionalDead`。殺棋過。不必把死子收到碗裡。假眼死形同樣：即將成劫不可當淨殺（8K T17）。

### 對照：13K S19 點眼

`docs/13K-kill.tsumego.json` 黑 R19 白 Q19 黑 S19。白一串，氣 P19／R18／T19 全與黑共。R18（三白一黑）、T19（邊上一白一黑）是假眼；P19 是真氣（牆外還有 O19）。

人眼：點眼死形，黑勝。舊算法兩處都缺：

1. **雙活**：`deadlockSeki` 把 2–4 口全共氣當成雙活。R18／T19 詰了會入氣，長得像老鼠偷油剩兩口入氣，分類成 `Seki` → 樹上 `黑下 S19 -> 白勝` → 對局失敗。
2. **做不成兩眼**：就算不當雙活，`ownerCanForceLife` 從 P19 灌到 O19 空點 > 8，直接當還能活 → 未定。ADR-0014 第 (6) 步本來要抓封死的假眼，但洪水啟發式沒把「假眼 + 一口外氣」算進去。

現況：共氣裡扣掉假眼，真氣≤1 → `isFalseEyeDead` → 無條件死。不把這條放進 `ownerCanForceLife`：收緊做活會改 `winningBlack`／做活點，small_trick 黑 S19 後應手會從 R19 漂成 T16／S17。測：`Kill13BugLoopTest`、`Kill13SolverTreeTest.afterR19TreeMustNotCallS19WhiteWin`、`SmallTrickSolverTest.afterS19WhiteResistsAtR19`。

### 對照：8K S17–S19–T18 彎三

`docs/8K-kill.tsumego.json` 黑 S17 白 S19 黑 T18。白氣 S18／T17／T19 全與黑共。T18 是點眼，空點成彎三。人眼：白做不成兩眼，黑勝。

舊算法缺在**做活當對殺**：BFS 連填 S18／T17／T19，提掉開局仍有三氣的 T18，Benson 成功，`canLive=true`。雙活在「做不成兩眼」之前，三口全共氣 → `Seki` → 樹上 `白下 S19 -> 黑下 T18 -> 白勝` → 對局失敗。

這不是缺「彎三形狀表」。ADR-0014 第 (6) 步的做活必須是填自己的眼位；提掉攻擊方是對殺。舊規則只禁「一次提兩顆」，一顆三氣的點眼漏掉了。求快把吃子當活，死活就錯。

現況：做活不可提開局時還有兩氣以上的攻擊子 → `canLive=false` → 不是雙活 → 無條件死。測：`Kill8BugLoopTest.afterS17S19T18IsKillSuccessNotWhiteWin`、`Kill8SolverTreeTest.afterS17TreeMustNotCallS19T18WhiteWin`。

同題 黑 T18 白 S18 黑 T17：盤上還沒單劫、S17 看起來像假眼、`canLive=false`，舊捷徑當淨殺／黑勝。其實白 T19 做成單劫，黑只能 S19 提劫。v1 淨殺才過，打劫失敗。不可因「劫還沒出現」就判無條件死。現況：白一手能走到**與目標有關**的劫殺、且黑除劫外提不掉目標 → 未定。牆上旁劫（開牆空出來的 Q13/P13）不算。T19 已是劫殺；S19 提劫仍是劫殺。測：`Kill8BugLoopTest.afterT18S18T17IsKoFightNotKillSuccess`、`afterT18S18T17SessionIsNotSuccess`、`DeadShapeTest.kill8KoTakeIsKoKillNotUnconditionalDead`。

### 對照：9K Q19 提五子

`docs/9K-kill-20260828.tsumego.json` 黑 P18 白 P17 黑 Q17 白 Q18 黑 Q16 白 P19 黑 Q19，提掉 Q18 與 R16–R19。盤上目標還剩 M18/M19、N17/O17、O19（P17/P19 是白但不是目標）。

人眼：剩餘白做不成兩眼，應立刻黑勝。舊實作在「已提掉一部分目標」之後仍用整盤氣洪水，且做活 BFS 走出 `O18…L17` 提掉包圍的 L17–L18 才 Benson，`classify` 未定，白搜 200s。現況：已提子則改用局部眼位，且做活不可提兩氣以上攻擊子（含一次兩顆）。此形無條件死 → 對局成功。測：`Kill9DeadShapeTest.afterQ19CaptureRemainingWhiteCannotMakeTwoEyes`。

還沒做：沒有死形表（刀把五、盤角曲四、彎三當形狀模板）；眼位 > 8 仍當未定，交給 AND–OR。彎三能殺是因為做活不算對殺，不是因為形狀表。

---

## v1 淨殺：打劫／雙活即白勝，對局停

契約已在 ADR-0002／0003／0009／0024 與 CONTEXT「失敗」。殺棋只認無條件死；劫殺、劫活、雙活、無條件活都是 **黑不能強迫過** → 搜尋 `Force.No` → 決策樹 **白勝** → 對局 **失敗**。Session `applyBlack`：`outcome != Unsettled` 就宣判，不再 `launchSearch`，不用再輪黑。`applyWhite` 的 `Refute` 同樣失敗。

葉子文案仍是黑勝／白勝（ADR-0026）。打劫是終局名，不是第三種樹葉；路徑寫 `… -> 白勝` 即「這枝淨殺失敗」。

**缺口（老鼠偷油 T15–T16–T17–S17–S16）：** 單劫禁立即回提（ADR-0004）讓搜尋在黑提劫後把白回提當非法，當成黑已贏劫繼續淨殺，樹上出現 `… -> 黑勝`，違反 ADR-0002「不當成對局下完」。要先分類：有單劫提、且除劫點外沒有提掉目標的著手 → 劫殺（本題劫）；旁劫仍可用非劫提（R19）→ 未定。`ownerCanForceLife(目前局面)` 不能當唯一門檻，因為做活搜尋會把回提當白自己的填子。測：`Kill7MouseOilResistTest`。

```
白目標 classifyKo:
  两次假設都能 Benson 活 → 無條件活
  有單劫提、且除劫點外沒有提掉目標的著手 → 劫殺   # 本題劫（老鼠偷油 S17／S16）
  做不成兩眼且劫仍在 → 劫殺                         # 8K 提劫
  否則 null（旁劫：R19 仍可提三子）
```

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

1. 證明對不對：`canForce`／分類／劫殺／雙活／點眼死形／做不成兩眼／相關區；測 `SessionSolverTest`、`SmallTrickSolverTest`、`Kill8BugLoopTest`、`Kill8SolverTreeTest`、`Kill7BugLoopTest`、`Kill7SolverTreeTest`、`Kill9DeadShapeTest`、`Kill13BugLoopTest`、`Kill13SolverTreeTest`
2. 應手穩不穩：`resistOrder`、`pickRefute`；測最長抵抗與做活點
3. 有路徑沒應手：先量 `Kill7ReplyLagTest` 的 firstPath／complete／done，再考慮證明輪 `yesKids` 交給 `pickResist`。顯示收束不會讓搜尋變快
4. 要更快：接上 `zonePattern`、加深 TT、或新 `Solver`（df-pn 等）
