# 詰碁 — study-LD-RZ 獨立前端＋Kotlin 引擎（v1 交接）

給**本機 Grok Build／CLI agent**。

定位：在詰碁 KMP（Compose Desktop + Wasm）裡掛一個**獨立小程式**，行為對齊論文配套程式 study-LD-RZ 的使用方式（開題 → 計算 → 寫 result → 顯示），用 Kotlin 重寫搜索，**不編譯 C++／Caffe2**。  
這不是改寫現有殺棋訓練機；現有 `solve/Solver.kt` 不准動。學會之後再考慮把想法併回詰碁本題。

領域詞仍跟 `CONTEXT.md`、`handoff.md`。本檔覆蓋「study-LD-RZ 這條菜單」。

---

## 1. 使用者可見契約（必須做成這樣）

Desktop **頂層選單**獨立一組，標題就叫 **study-LD-RZ**（不要叫 LD-RZ、不要塞進 File → Samples）。

下拉三項，順序固定：

| 項 | 行為 |
|---|---|
| **1. Open** | 檔案選擇器，開 study-LD-RZ 的題目 JSON（趙治勳卷，如 `chao_vol1_p088.json`）。開成功後**盤面顯示該詰棋**：子、關鍵子（可用現有紅圈）、`region` 可標示。不開搜索。 |
| **2. Calculate** | 對**目前已 Open 的題**跑 Kotlin 引擎。結束後在倉庫根目錄寫出兩檔（檔名對齊原 README）：`result/result_<stem>.json`、`result/uct_tree_<stem>.sgf`。例：題檔 `chao_vol1_p088.json` → `result/result_chao_vol1_p088.json` 與 `result/uct_tree_chao_vol1_p088.sgf`。 |
| **3. Show** | 讀剛才（或使用者另選）的 result JSON＋SGF，把結果疊回盤面／訊息列：狀態、第一手、region、節點數、耗時。SGF 用現有或簡易變化顯示即可；不要求原版 Windows 著色檢視器。 |

未 Open 就按 Calculate／Show：訊息列提示，不崩潰。  
Calculate 進行中：三項停用，結束後恢復。  
Wasm：Open 可用內嵌／拖放 fixture；寫 `result/` 僅 Desktop（檔案 I/O 放 desktopMain）。

工作目錄預設為**詰碁倉庫根**（與 `docs/refs/study-LD-RZ/tsumego/`、`result/` 相對）。Calculate 若 `result/` 不存在就建立。`result/` 產出物應在 `.gitignore`（不要把算完的樹推進 GitHub）。

---

## 2. 這是新程式，只借殼

```
study-LD-RZ 原版     = C++ CGI + Caffe2 + Docker，Mac 跑不起來
本模組               = 同一開題／輸出檔名習慣，Kotlin 寫在 tsumego 工程裡
詰碁本題 solve/      = 殺棋訓練機，互不 import 搜索主迴圈
```

可以共用：`board/` 規則、盤面繪圖、座標顯示、目標子紅圈。  
不可以共用：`Solver.kt` 最長抵抗、Session 應手、正式題庫成功／失敗統計、Q20 存檔。

參考樹（只讀、已 gitignore）：

```
docs/refs/study-LD-RZ/
  tsumego/*.json
  candidate.list
  cfg/
  CGI/   MCTPS/
```

題目來源：Open 時讓使用者選 `docs/refs/study-LD-RZ/tsumego/` 下檔案（或任意相容 JSON）。不要把該目錄加進 Gradle source set。

---

## 3. 要做／不要做

### 要做（v1）

1. 套件 `com.neojou.tsumego.solverLdrz`。
2. 讀原版 JSON → 盤面＋元資料（第 5 節）。
3. Open 後畫題（遮罩盤優先 `masked_sgf_str`）。
4. Calculate：獨立搜索＋寫出兩個輸出檔（第 6 節檔名與欄位）。
5. Show：讀輸出檔並顯示。
6. 測試：座標、JSON、微盤搜索、輸出檔名／必填欄位。

### 不要做

- CMake、Docker、Caffe2、JNI、複製 C++ 進 composeApp。
- 改 `Solver.kt`、`Classify.kt` 順序、既有 ADR、正式題庫載入器。
- FTL 網路、RZS-PT、論文 83 題／五分鐘 KPI。
- 要求第一手等於 `answer_firstmove`（可寫進 result 當對照欄，搜索禁止寫死）。
- 原版 Windows `editor.exe` 著色檢視器。
- 把 LD-RZ JSON 存成詰碁 Q20。

---

## 4. 目錄

```
composeApp/src/commonMain/kotlin/com/neojou/tsumego/solverLdrz/
  LdrzProblem.kt
  LdrzJson.kt              # 入：題目 JSON
  LdrzCoord.kt             # SGF 兩字母 ↔ 詰碁 A–T 跳 I，A1 左下
  LdrzZone.kt
  LdrzSearch.kt
  LdrzResult.kt            # 記憶體結果
  LdrzOutput.kt            # 出：result_*.json + uct_tree_*.sgf 字串
  LdrzSolver.kt
  LdrzSession.kt           # Open 後的目前題；Calculate／Show 狀態

composeApp/src/desktopMain/kotlin/com/neojou/tsumego/solverLdrz/
  LdrzFiles.kt             # 開檔、寫 result/
  LdrzMenu.kt              # 頂層選單 study-LD-RZ → Open / Calculate / Show

composeApp/src/commonTest/kotlin/com/neojou/tsumego/solverLdrz/
  LdrzCoordTest.kt
  LdrzJsonTest.kt
  LdrzSearchTinyTest.kt
  LdrzOutputTest.kt        # 檔名 stem、JSON 必填鍵、SGF 可被當 FF[4] 解析
```

菜單掛進現有 Desktop 頂層 MenuBar 的**新 Menu**，不要改現有 File 語意。

---

## 5. 輸入 JSON（Open）

對齊 `docs/refs/study-LD-RZ/tsumego/chao_vol1_p031.json` 等。

| 欄位 | v1 |
|---|---|
| `masked_sgf_str` | 優先當盤面 |
| `rawsgf` | 後備 |
| `turn_color` | `b`／`w` |
| `winning_color` | 只記錄 |
| `black_crucial_stone`／`white_crucial_stone` | 關鍵子 |
| `black_search_goal`／`white_search_goal` | `TOLIVE`／`TOKILL` |
| `black_ko_rule`／`white_ko_rule` | v1 可先全禁止同型反覆 |
| `answer_firstmove` | 寫進輸出對照，不綁搜索 |
| `region` | 可下白名單 |
| `filename`／`category` | 顯示與 stem |

`stem`：題檔名去掉 `.json`（`chao_vol1_p088.json` → `chao_vol1_p088`）。Calculate 輸出只用這個 stem，與原 README 例相同。

座標：SGF 兩字母；`LdrzCoord` 必須有測試（含跳 I）。轉換錯則整題作廢。

---

## 6. 輸出必須對齊原程式檔名

原 README：

```
result/result_chao_vol1_p088.json
result/uct_tree_chao_vol1_p088.sgf
```

v1 **檔名一字不差**（相對倉庫根）。內容無法位元級複製原 CGI（無 FTL、無同樣模擬），但要讓人能對照、用普通 SGF 軟體打開樹。

### 6.1 `result/result_<stem>.json`

必填（原 README 點名的鍵用同一名字）：

```json
{
  "engine": "kotlin-v1",
  "problem": "chao_vol1_p088",
  "source_json": "chao_vol1_p088.json",
  "status": "ALIVE",
  "first_move_sgf": "sr",
  "answer_firstmove": "sr",
  "NumSimulations": 1840,
  "Time": 1.23,
  "zone_count": 42,
  "goal_black": "TOLIVE",
  "goal_white": "TOKILL",
  "turn_color": "b",
  "message": ""
}
```

- `NumSimulations`：搜索節點數（對齊原統計名；v1 無 NN 模擬也用此鍵）。
- `Time`：秒、小數。
- `status`：`ALIVE`／`DEAD`／`UNSETTLED`／`ERROR`。
- `first_move_sgf`：SGF 兩字母；無則 `null`。
- 可加欄但不准拿掉上表必填。
- 不要寫成詰碁 Q20。

### 6.2 `result/uct_tree_<stem>.sgf`

標準 FF[4] 文字，Sabaki／GoReviewPartner 能開：

- 根節點：`SZ`、`AB`／`AW`、`PL`、關鍵子可用 `TR`、region 可用 `MA` 或註解列出。
- 主線：Calculate 得到的第一手及一條變化（能多層就多層，v1 至少根＋第一手）。
- 註解標 `RZONE` 點列、`WIN` 第一手；原版檢視器綠區／紅勝著 v1 **不實作二進位著色**，用標記即可。
- 檔名仍叫 `uct_tree_`（對齊原程式），即使 v1 搜索不是完整 UCT。

Show：讀這兩個檔；JSON 缺檔就提示先 Calculate。

---

## 7. 搜索 v1（獨立、求正確不求辭典全解）

```kotlin
interface LdrzLifeDeathSolver {
    fun solve(problem: LdrzProblem): LdrzResult
}
```

- 著點 ⊆ JSON `region` 空點。
- `TOKILL` 且該色關鍵子全離盤 → 對被殺方 `DEAD`。
- 兩真眼／可呼叫現有 `classify` 無條件活 → `ALIVE`（**不准改 classify 順序**）。
- 節點上限預設 20_000，超過 `UNSETTLED`。
- 無 CNN、無 pattern 表、不呼叫 `Solver.kt` 最長抵抗鍵。
- 解題程度預期：小眼常型；趙治勳卷多數題會是 UNSETTLED——仍要寫出兩個檔，狀態如實。

---

## 8. 測試

```bash
./gradlew :composeApp:desktopTest --tests com.neojou.tsumego.solverLdrz.*
./gradlew :composeApp:compileKotlinDesktop
```

- `LdrzCoordTest`
- `LdrzJsonTest`：內嵌 tiny JSON；缺欄不崩。
- `LdrzSearchTinyTest`：微盤活／死。
- `LdrzOutputTest`：`stem=chao_vol1_p088` → 兩個檔名；JSON 含 `NumSimulations`、`Time`；SGF 含 `FF[4]` 與 `SZ`。

`docs/refs/...` 不存在時不要 fail 整組測試。既有 `Kill*` 測試禁止改壞。

---

## 9. 實作順序

1. `LdrzCoord`＋測  
2. `LdrzProblem`／`LdrzJson`（`masked_sgf_str` → AB／AW／SZ／PL）＋測  
3. 轉現有 `board`（不開 Session 搜索）  
4. Open：選單＋畫盤＋關鍵子／region  
5. `LdrzZone`＋`LdrzSearch` 微盤  
6. `LdrzOutput`＋寫 `result/`  
7. Calculate／Show  
8. 可選：本機若有 `chao_vol1_p088.json`，Open＋Calculate 產出兩個檔（不要求第一手等於答案）

---

## 10. 授權

只讀算法與 JSON，不複製 C++ 進 composeApp。可註：概念參考 Shih et al., IEEE ToG / arXiv:2512.21365。題目與 `result/` 不上 GitHub。

---

## 11. 完成定義

1. 頂層選單 **study-LD-RZ** → Open／Calculate／Show。  
2. Open 趙治勳 JSON 能顯示詰棋。  
3. Calculate 寫出 `result/result_<stem>.json` 與 `result/uct_tree_<stem>.sgf`。  
4. Show 能讀回並顯示狀態／第一手。  
5. `solverLdrz` 測試綠、Desktop 能編譯；不碰 `Solver.kt` 主迴圈；無 Caffe2／Docker／CMake 新依賴。
