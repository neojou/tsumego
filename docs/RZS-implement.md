# 詰碁裡的 RZS：現況對照 `docs/RZS.md`

本檔記述**程式實際在做什麼**，以及它與 `docs/RZS.md`（Shih et al. Relevance-Zone Based Search 的白話）差在哪。詞表跟 `CONTEXT.md`。概念來源：`docs/RZS.md`、AAAI / arXiv:2112.02563、ToG / arXiv:2512.21365。

倉庫裡有**兩條**相關搜尋，不要混：

| 路徑 | 用途 | 主檔 |
|---|---|---|
| **study-LD-RZ** | 頂層選單 Open／Calculate／Show | `composeApp/.../solverLdrz/` |
| **訓練機** | 殺棋對局、最長抵抗 | `solve/Solver.kt` + `solve/RelevanceZone.kt` |

`docs/RZS.md` 講的是論文那套「證明活 → 反推相關區 → 空手剪必應」。下面以 **study-LD-RZ（Calculate）** 為主；訓練機只在對照時出現。兩條路徑**不互相 import 搜尋主迴圈**（handoff：不改 `Solver.kt`）。

---

## 1. 論文循環（`RZS.md` 濃縮）

```
搜一手 → 證明活（UCA）→ 反推出 RZ
     → 事後判定區外著手是空手 → MustPlay ∩ RZ → 再搜
```

**不是**「先猜一塊矩形，只搜那塊」。

AND 節點（攻擊方）：候選做 **intersection**（必應區縮小）。  
父節點要把各條仍活的證明合在一起時：相關區做 **union**。  
終局：Benson **無條件活（UCA）**。區太小、證明依賴區外時：**dilation** + Consistent Replay（CR）。  
NN／FTL 只做 **著手排序**，不負責剪枝正確性。

---

## 2. 對照表（`RZS.md` 節次 × 現況）

| `RZS.md` | 論文要的 | study-LD-RZ | 訓練機 |
|---|---|---|---|
| §1 AND–OR | 做活方 OR、殺方 AND | 有：`forceOr`／`forceAnd`（做活方＝`defender`） | 有：黑 OR／白 AND（題型過＝Yes） |
| §2 RZ-1 | 區內同型 ⇒ 區外亂下仍活 | **沒有**形式化檢查 | **沒有** |
| §3 終局 UCA | Benson 無條件活當 WIN | **有**：`evaluate` → `bensonAlive` | `classify` 無條件活只是五種終局之一；殺棋過只認無條件死 |
| §4 事後空手 | 先搜強手，再看是否在子 RZ 外 | **部分**：`isNullMove` 有寫，但主路徑常先 `fillUntilUca`（脫先連填）才縮區 | **較接近**：`andMoves`／`orMoves` 搜完 child 才 `isNullMove` + `retainMustPlay` |
| §5–8 MustPlay ∩ Z | AND 每證完一手就交相關區 | **弱**：`forceAnd` 用「當前 `currentZone`／vs-pass 區」**跳過**區外手，不是迴圈裡 `mustPlay = mustPlay ∩ childRZ` | **有**：`pending.clear()` + `retainMustPlay(moves, child.zone, searched)` |
| §9 intersection | 多條活證明的必應＝∩ | **沒有**顯式 ∩ 多個 child RZ | 每次空手用**該 child** 的 zone 重切 pending（近似逐次 ∩） |
| §10 union | 父 RZ＝∪ 子 RZ | **有形狀**：`liveZones` 再 `dilate(flatten)` | **有**：`yesZones`／`noZones` flatten 再 dilate |
| §11 pseudocode | `solve` 回傳 `(WIN/LOSS, RZ)` | 回傳 `LdrzSearchVal(status, line, zone)` | `Force.Yes/No(..., zone)` |
| §12 CR + dilation | 重播合法、提子／連通不變才算區 | dilation **有**（棋串＋氣＋鄰）；**沒有** CR 檢查 | 同左：`dilate` 有，CR 無 |
| §13 NN 排序 | Policy 找強手，RZS 剪枝 | **無 NN**；`eyeVitalScore` 規則排序 | **無 NN**；氣區／做活點／鄰空（ADR-0011） |
| §14 FTL／MCTS | 嵌入 AlphaZero | **無** | **無**（`MonteCarlo.kt` 未接入） |

---

## 3. study-LD-RZ 現在怎麼走（對應程式）

### 3.1 檔案

```
solverLdrz/
  LdrzProblem.kt     題、defender／關鍵子、toSearchPosition()
  LdrzJson.kt        入 JSON（masked_sgf_str、region、goal）
  LdrzCoord.kt       SGF 兩字母 ↔ 詰碁 A–T 跳 I
  LdrzZone.kt        seed／terminalAlive／terminalDead／dilate／searchPoints／isNullMove／mustPlay
  LdrzSearch.kt      forceLive／forceOr／forceAnd／fillUntilUca／evaluate
  LdrzSolver.kt      Calculate 入口、節點／時間上限、組 LdrzResult
  LdrzOutput.kt      result_*.json、uct_tree_*.sgf
  LdrzSession.kt     Open 後狀態、relevanceZone、進度字
  LdrzMenu.kt        選單與盤面（zoneFill 淺綠）
  LdrzFiles.kt       Desktop 寫 result/

ui/BoardView.kt      zoneFill 淺綠底
Tsumego.kt           Open／Calculate／Show 掛選單、進度 120ms 刷新
classify/Classify.kt bensonAlive（只呼叫，不改順序）
```

訓練機對應（另一條）：

```
solve/Solver.kt           canForce、orMoves、andMoves
solve/RelevanceZone.kt    terminalRelevanceZone、dilate、isNullMove、retainMustPlay
```

### 3.2 角色

論文例子常是「白求活、黑來殺」。本模組用 JSON：

- `defender`＝`TOLIVE` 那色（p189 為黑）
- `attacker`＝`TOKILL` 那色（p189 為白）
- OR＝`forceOr`＝defender 要找一手仍能活
- AND＝`forceAnd`＝attacker 每一手（區內）都無法殺

WIN＝`ALIVE`（關鍵子任一在 `bensonAlive` 裡），LOSS＝`DEAD`（關鍵子全離盤）。劫／雙活不當終局葉（與訓練機 v1 殺棋表不同）。

### 3.3 Calculate 主流程

`LdrzSolver.solve`：

1. `problem.toSearchPosition()`：用關鍵子 **dilate 1 次** 的包圍盒當題目盤，非十九路真盤邊改 **牆**（讓 Benson 比較容易成形；這是固定局部盤，**不是**論文 RZ）。
2. `LdrzSearch.run(root, turnColor)`。
3. 寫 `result/result_<stem>.json`、`result/uct_tree_<stem>.sgf`。

`LdrzSearch.run`：

```
currentZone = LdrzZone.seed(dilate 3，裁 JSON region)   // 先猜局部，違反「不要先規定只搜局部」
if evaluate(root) 已 UCA／已提光 → 終局
else forceLive(root)
```

`evaluate`（`LdrzSearch.kt`）：

- 關鍵子都不在盤上 → `DEAD` + `terminalDead`
- 任一關鍵子 ∈ `bensonAlive(defender)` → `ALIVE` + `terminalAlive`
- 否則繼續搜（不呼叫完整 `classify`）

### 3.4 做活探測 `fillUntilUca`（論文沒有、本實作有）

攻擊方**假設一直脫先**，defender 在 `searchPoints`（氣＋再一圈空點）裡 BFS 連填，直到 Benson 或 2000 節點／16 手。

這比較像 `RZS.md` §4 說的 **Lambda／先試 pass**，論文明確說 pass 往往是廢棋、不該當主策略。本實作卻用它來：

- 得到一條「脫先也能活」的填子線（當 PV／hint）
- 把該終局的 `terminalAlive` dilate 成 `currentZone`
- `forceAnd`：若攻擊方下完仍 `fillUntilUca != null`，直接當這手不殺、**不再往下 AND–OR**

因此綠區常常「看起來對」（確實是做活塊），但證明強度是「脫先連填」，不是「對手每手都應完仍 UCA」。

### 3.5 OR／AND 與論文 pseudocode 的差別

論文 AND（攻擊方、要證明仍活）：

```
mustPlay = 全部合法
while mustPlay 非空:
    搜一手
    若 LOSS → 整節點 LOSS
    若這手在 childRZ 外 → mustPlay = mustPlay ∩ childRZ
    否則從 mustPlay 拿掉這手
parentRZ = ∪ solutionZones
```

本實作 `forceAnd`：

```
vsPass = fillUntilUca(脫先連填)
zone = dilate(vsPass.zone) 或 seed
for move in orderedMoves:          // 一開始就不是全盤合法手
    if vsPass != null and move 在 zone 外: skip   // 事前用區外跳過，不是事後 ∩
    if evaluate DEAD: return DEAD
    if evaluate ALIVE or fillUntilUca 仍成功: 記 zone，continue
    child = forceLive(defender)
    if DEAD: return DEAD
if vsPass != null: return ALIVE    // 即使有 UNSETTLED 分枝也可能回 ALIVE
parent zone ≈ ∪ liveZones 再 dilate
```

缺的關鍵步驟：

- 不是「先正常搜強手再事後判空手」，而是 **先 seed／searchPoints 限著點**，再 **脫先探測**。
- `LdrzZone.mustPlay` 寫了 `listed ∩ zone`，**`forceAnd` 沒走 while-mustPlay 迴圈**，幾乎沒用到。
- 多條 child RZ **沒有 intersection**；只 union 進 `liveZones`。
- `vsPass != null` 就宣告 `ALIVE`，攻擊方區內未搜完或 `fillUntilUca` 假活都會過。

OR `forceOr`：試 defender 各手（hint 第一手優先），child `ALIVE` 的用 `eyeVitalScore` 選一線做眼急所（p189 要 R1 不要 T2）。論文 OR 是「任一 WIN 立刻回」；這裡為選急所會掃過多手。

### 3.6 區怎麼長出來

| 函數 | 何時 | 內容 |
|---|---|---|
| `LdrzZone.seed` | 搜尋開始、`orderedMoves` 後備 | 關鍵棋串＋氣，dilate 3，∩ JSON `region` |
| `searchPoints` | 候選手 | 氣＋氣的鄰空（做眼空間），不是 JSON 整塊 mask |
| `terminalAlive` | UCA 葉 | 已 Benson 的關鍵棋串＋氣 |
| `terminalDead` | 提光 | 關鍵子＋鄰＋棋串＋氣 |
| `dilate` | 回傳父區 | 加入著手、同棋串、氣、正交鄰，裁 `rect` |

JSON `region`（automask `MA`）只是**可下白名單**，Open 時小綠方標；Calculate 後 `BoardView.zoneFill` 畫的是 `LdrzSession.relevanceZone`（搜尋區），淺綠底。

### 3.7 著手從哪來

`orderedMoves`：`searchPoints ∩ (currentZone 或 seed)`，不是 19 路全部合法手。氣優先，其餘按鄰己方子。攻擊方停在前、做活方停在後。

這是 **Kishimoto 式人工／規則限區**，正是 `RZS.md` 開頭對比的「先規定只搜局部」。論文 RZ 應從證明長出來之後才限 MustPlay。

`eyeVitalScore`：一線、對準棋串、靠角、二線空＋三線己子。**規則排序 ≠ NN。**

### 3.8 盤面與輸出

- 顯示：`toPosition()` 全遮罩盤。
- 搜尋：`toSearchPosition()` 局部牆盒。
- 進度：`LdrzProgress.phase`（決定相關區／探測做活／驗證攻擊方），`Tsumego.kt` 約 120ms 抄到側欄。
- 結果：`status`、`first_move_sgf`、`NumSimulations`、`Time`、`zone_count`；SGF 的 `MA` 用搜尋區。

---

## 4. 有照 `RZS.md` 的部分

1. **目標是證明死活，不是勝率。** `ALIVE`／`DEAD`／`UNSETTLED`，無 eval 分數。
2. **UCA 葉用 Benson。** `classify.bensonAlive`，與 §3 一致。
3. **終局長區再 dilate。** `terminalAlive`／`terminalDead` + `LdrzZone.dilate`，與訓練機 `RelevanceZone.dilate` 同類（棋串、氣、鄰、著手）。
4. **空手定義。** `isNullMove`＝著手 ∉ zone（訓練機同樣）。
5. **AND 找到殺則整節點死。** `forceAnd` 一碰到 `DEAD` 就 return。
6. **父區用子區聯集再膨脹。** `liveZones.flatten` + dilate，對應 §10 union。
7. **排序與剪枝分開寫。** 即使沒有 NN，急所分也只影響先試哪手，不代替 RZ 證明（§13 的分工精神）。
8. **訓練機 `andMoves`／`orMoves` 較像 §11：** 搜完 child 才 `isNullMove` + `retainMustPlay`，這是 post-hoc 空手。Calculate **沒有**接這段。

---

## 5. 沒有照 `RZS.md` 的部分

1. **先 seed／searchPoints／牆盒，再搜尋。** 論文禁止「先猜 RZ 只搜那塊」。`toSearchPosition`、`seed(dilateTimes=3)`、`searchPoints` 都是事前局部。
2. **主證明靠 `fillUntilUca`（攻擊方脫先連填）。** 論文 §4 批評的就是先試 pass／null。本實作把它當預設活證明。
3. **AND 沒有 MustPlay 迴圈與 ∩。** `mustPlay()` 幾乎死碼；區外手是 **事前 skip**，不是「這手證明完發現在 childRZ 外再 ∩」。
4. **沒有 RZ-1／CR。** 不會驗證「區內同型、區外任意仍合法可重播」。dilation 是固定膨脹，不是「不滿足 CR 再擴」。
5. **`forceAnd` 在 `vsPass != null` 時可直接 `ALIVE`。** 區內攻擊若 `fillUntilUca` 仍成功就 `continue`，等於再給做活方連填、攻擊方不再下——p189 曾把 T2 連回誤當第一手。
6. **OR 為選急所掃過多手，不是找到第一個 WIN 就停**（論文 OR 可早退）。
7. **無 NN、無 FTL、無 pattern／R-zone TT**（只有局面字串 TT，且 ply 被 `coerceAtMost(2)` 壓過）。
8. **劫不當葉。** 論文 UCA 題可不管劫；本倉 JSON 有 ko rule，搜尋未接。
9. **訓練機 RZS 不能當 Calculate。** 訓練機證明的是「黑能否強迫殺棋過」，葉子是 ADR-0014 全套終局，不是 study-LD-RZ 的 TOLIVE／TOKILL。

---

## 6. 用論文循環套在 p189 上會變成什麼

論文期望：

```
正常搜（或 NN 建議）R1
→ 變化下到 Benson
→ Z = 活棋串＋眼＋維持眼的鄰
→ 白／黑在 Z 外的手事後變空手
→ MustPlay ∩ Z 直到空 → 證明活
```

現況：

```
牆盒裁到 O–T、1–5
seed／searchPoints 約十餘點
fillUntilUca：脫先連填（T2／Q1／R2… 也能 Benson）
currentZone ← 該填子終局 dilate
forceAnd：區外 skip；區內一手後再 fillUntilUca 仍活 → 當不殺
forceOr：在「脫先意義下能活」的著手裡用 eyeVitalScore 選一線急所（R1）
```

所以：綠區往往像對的做活塊；`status`／第一手是否等於書上 `qs`，取決於探測與排序，**不是**完整 RZS 證明樹。

---

## 7. 若要對齊 `RZS.md` 的下一步（只記方向，不在本檔實作）

按論文順序，而不是再加排序權重：

1. AND：`mustPlay` 從合法手（或 JSON region 空點）起，**每手搜完**再用 `child.zone` 做 ∩；空手才整批切掉。
2. 葉子仍只認 UCA／提光；**不要**用「脫先連填成功」當 WIN。
3. `fillUntilUca` 最多當 **排序 hint**，不當證明。
4. dilation 之後補 CR（提子、氣、自殺、連通）不滿足就再擴區。
5. 不要把 `toSearchPosition` 牆盒當成 RZ；RZ 只從證明長。
6. 訓練機 `andMoves` 的 post-hoc 空手可當參考，但不可 import `Solver.kt` 最長抵抗。

---

## 8. 測試落點

```
composeApp/src/commonTest/.../solverLdrz/
  LdrzCoordTest.kt
  LdrzJsonTest.kt
  LdrzSearchTinyTest.kt    微盤活／死、有 zone
  LdrzZoneTest.kt          seed／dilate／terminalAlive
  LdrzOutputTest.kt
composeApp/src/desktopTest/.../solverLdrz/
  LdrzChaoFileTest.kt      p189：ALIVE、第一手 R1／qs（檔存在才跑）
```
