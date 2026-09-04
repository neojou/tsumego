RZS（Relevance-Zone Based Search）的核心其實可以用一句話概括：

> **不是先規定「只搜尋局部」，而是每當找到一個可以證明「活」的結果後，從這個證明反推出一個最小相關區域；之後對手只需要搜尋能干擾這個區域的手。**

這種做法比單純設定一個固定矩形 ROI 強很多，因為那個「相關區域」會隨著變化動態改變，而且它具有「證明」意義，而不只是 heuristic。原始 RZS 論文就是針對這點設計的。([arXiv][1])

## 1. 先從普通死活搜尋說起

假設白棋的目標是「做活」。

普通 AND-OR tree 可以寫成：

* 白棋節點 = **OR node**：只要找到一手能活即可。
* 黑棋節點 = **AND node**：必須證明黑棋所有可能應手，白棋都能活。

問題在於黑棋在 19×19 上理論上可能有兩三百個合法著點。

例如局部死活明明只發生在右下角：

```text
┌─────────────────┐
│                 │
│                 │
│                 │
│             ●●  │
│            ○○●  │
│             ○●  │
└─────────────────┘
```

普通 proof search 在黑方節點概念上要回答：

```text
黑 A1
黑 B1
黑 C1
...
黑 Q16
黑 R16
...
```

可是很明顯：

```text
黑 A1
```

跟右下角白棋能不能活，通常根本沒有關係。

這就是 RZS 要消掉的巨大 branching factor。

---

# 2. Relevance Zone 的正式概念

假設目前 position 是 \(p\)，找到一個區域 \(Z\)。

如果：

$$
\beta(p')|_Z = \beta(p)|_Z
$$

也就是另一個棋局 \(p'\) 在 \(Z\) 裡面的棋形完全相同，而 **\(Z\) 外怎麼變都不重要**，並且所有這種 \(p'\) 都仍然是「白活」，那麼 \(Z\) 就是這個 position 的 **Relevance Zone**。論文把這稱為 RZ-1 性質。([arXiv][1])

用白話說：

> 只要我保證這塊區域長這樣，外面的棋盤全部亂下，我還是活。

這是一個很強的條件。

---

# 3. 最容易理解的例子：兩眼已經完成

例如：

```text
  A B C D E
1 ● ● ● ● ●
2 ● ○ ○ ○ ●
3 ● ○ . ○ ●
4 ● ○ . ○ ●
5 ● ● ● ● ●
```

假設中間白棋已經形成 Benson 意義下的 unconditional life。

那麼系統可以說：

```text
Z = {
  白棋本身，
  兩個眼位，
  維持這兩眼所需的周圍關係
}
```

只要 \(Z\) 裡的棋形沒改：

```text
左上角下黑棋？    無關
右上角下黑棋？    無關
棋盤中央下黑棋？  無關
```

白棋還是活。

因此那些位置不必進 solution tree。

論文實作中，終局是否已達目標主要利用 Benson 的 **UCA（Unconditionally Alive）** 判定；原始研究特別把目標限制為能以 UCA 證明安全的死活問題。([arXiv][1])

---

# 4. RZS 最漂亮的一點：它不是先找 RZ

傳統 Lambda Search 比較像：

```text
假設黑棋 pass
      ↓
看看白棋是否能活
      ↓
反推出黑棋真正必須下的位置
```

問題是：

> pass 通常是一手很差的棋。

所以你花很多搜尋算力去試 null move，可能完全浪費。

RZS 改成：

```text
正常搜尋強手
↓
找到結果
↓
再回頭看：
「剛剛這手其實是否根本不相關？」
```

也就是論文所說的 **post-hoc null move detection**。([arXiv][1])

這點非常重要。

---

# 5. 具體演算法：假設黑先攻、白求活

假設現在是：

```text
Black to move
```

黑棋目標：

> 殺死白棋。

白棋目標：

> 活。

首先：

```text
MustPlay = 所有合法黑棋著點
```

開始時可能有 200 多個。

---

## 第一步：黑棋試一手

假設 AI/MCTS 認為：

```text
Black D2
```

最值得搜尋。

於是搜尋：

```text
Black D2
   ↓
White F4
   ↓
Black ...
   ↓
White ...
   ↓
白棋成功 UCA
```

現在白棋不只得到：

```text
WIN
```

還得到一個：

```text
RZ = Z1
```

---

# 6. 現在看 D2 在不在 Z1

這是核心。

假設：

```text
D2 ∉ Z1
```

意思是：

> 我剛剛已經證明，只要 Z1 的局面保持這樣，不管 Z1 外發生什麼，白棋都活。

可是黑 D2 剛好就在 Z1 外。

因此：

```text
Black D2
```

其實根本沒有影響證明。

所以 D2 是：

> **null move**

這不是事前猜的。

而是事後「證明」它是 null move。

論文正是利用這個邏輯：如果某手落在子節點的 RZ 外，那麼這手對該證明而言等價於無關著，因此可以用該 RZ 大幅縮減 must-play region。([arXiv][1])

---

# 7. 最大的剪枝就在這裡

既然：

```text
Z1 外所有黑棋
```

都不影響白活，那黑棋真正值得試的只剩：

```text
MustPlay ← MustPlay ∩ Z1
```

例如本來：

$$
|MustPlay| = 220
$$

現在：

$$
|Z_1| = 12
$$

可能瞬間變成：

```text
220 → 12
```

這就是 RZS 為什麼對 tsumego 特別有效。

---

# 8. 再搜尋 Z1 裡的一手

例如現在只剩：

```text
E2
F2
G1
...
```

選：

```text
Black E2
```

搜尋：

```text
Black E2
   ↓
White D1
   ↓
...
   ↓
White lives
```

得到另一個 relevance zone：

$$
Z_2
$$

現在可以再縮：

$$
MustPlay \leftarrow MustPlay \cap Z_2
$$

例如：

```text
原本 220
↓
Z1
12
↓
Z2
4
```

再搜尋另一手：

```text
Black F2
```

得到：

$$
Z_3
$$

那麼：

$$
MustPlay
=
LegalMoves
\cap Z_1
\cap Z_2
\cap Z_3
$$

最後可能變成：

```text
MustPlay = ∅
```

這時候就證明：

> 黑棋沒有任何一手能阻止白棋活。

因此此節點：

$$
WIN
$$

這跟論文 Hex / Go 範例的推導完全一致：每解掉一個防守分支，就用得到的 RZ 去縮 must-play region，最後如果其中已無合法防守手，就完成整個 AND node 的證明。([arXiv][1])

---

# 9. 為什麼是「intersection」？

這點很漂亮。

假設：

```text
黑 A
→ 白能活
→ RZ = Za
```

因此黑要避免這條白活證明：

```text
黑棋必須動到 Za
```

另外：

```text
黑 B
→ 白也能活
→ RZ = Zb
```

那麼黑下一手如果真的想改變結果，就必須同時避開這兩種證明，所以只能在：

$$
Z_a \cap Z_b
$$

裡面找。

再多一個：

$$
Z_a \cap Z_b \cap Z_c
$$

越搜：

> must-play region 越小。

這是一種非常強的 **AND-node branch reduction**。

---

# 10. 那父節點的 RZ 怎麼來？

這裡剛好相反。

假設我們已經證明三條應手：

$$
Z_1,\ Z_2,\ Z_3
$$

整個父節點的勝利策略可能需要依情況使用其中不同的 solution。

因此父節點的 relevance zone 可以取：

$$
Z_{\text{parent}}
=
Z_1 \cup Z_2 \cup Z_3
$$

也就是：

> **搜尋候選防守手時做 intersection；產生完整勝利證明的 RZ 時做 union。**

這兩個方向很容易混淆。

論文的 Go 範例也是：各子分支的 RZ 最後 union 成父節點的 RZ，再往祖先節點傳遞。([arXiv][1])

---

# 11. 所以整個 RZS 可以簡化成這個 pseudocode

概念上：

```text
solve(position):

    if goalAchieved(position):
        return WIN, compute_terminal_RZ(position)

    if OR-player-to-move:

        for move in orderedMoves(position):

            result, childRZ = solve(play(move))

            if result == WIN:
                parentRZ =
                    adjust_RZ(childRZ, move, position)

                return WIN, parentRZ

        return LOSS


    else:   // AND player

        mustPlay = allLegalMoves(position)
        solutionZones = []

        while mustPlay not empty:

            move = bestMove(mustPlay)

            result, childRZ = solve(play(move))

            if result == LOSS:
                return LOSS

            solutionZones.append(childRZ)

            if move outside childRZ:
                // this move is retroactively null
                mustPlay = mustPlay ∩ childRZ
            else:
                mustPlay.remove(move)

        parentRZ = union(solutionZones)

        return WIN, parentRZ
```

實際程式當然更複雜，尤其要處理：

* captures
* liberties
* ko
* 重複局面
* zone dilation
* legal move dependency
* consistent replay conditions
* block connectivity

但核心邏輯就是上面這個。

---

# 12. 「只看 RZ」會不會有 bug？

這是整個演算法最難的地方。

例如 RZ 是：

```text
○ ○ ○
○ . ○
○ ○ ○
```

你不能單純說：

> RZ 外都不重要。

因為 RZ 外可能有一顆黑棋：

```text
      ●
○ ○ ○
○ . ○
○ ○ ○
```

那顆黑棋可能：

* 跟內部 block 相連；
* 改變氣；
* 被提掉；
* 影響內部合法性；
* 造成 ko；
* 造成 suicide legality 改變。

所以必須確保：

> **如果把這個 solution tree「重播」到另一個 RZ 內棋形相同、RZ 外不同的棋局上，每一步仍然合法，而且 capture/連通關係不會改掉原來的證明。**

這就是論文提出的：

### Consistent Replay Conditions

也就是 CR conditions。

如果目前 \(Z\) 不滿足這些條件，就需要：

### Zone Dilation

把 RZ 往外擴。

repository 的 supplementary material 也特別包含了 zone dilation 與 CR conditions 的說明。([GitHub][2])

所以 RZS 並不是：

```text
找到局部區域
→ 粗暴忽略外面
```

而是：

```text
找到候選 RZ
↓
檢查 solution 是否可在所有相同 RZ pattern 上重播
↓
如果依賴外部
    擴張 Z
↓
直到證明對 Z 外棋形完全不敏感
```

這才是它具有 solver correctness 的原因。

---

# 13. 它跟 KataGo 的 Policy Network 怎麼合作？

這也是我覺得這篇研究很漂亮的地方。

RZS **不負責猜好棋**。

AlphaZero/MCTS 負責：

$$
P(a|s)
$$

找：

> 「哪一手看起來最有可能是正解？」

RZS 負責：

> 「哪些手根本不需要再證明？」

因此兩者分工：

```text
Neural Network
      ↓
候選排序
      ↓
MCTS / DFS
      ↓
找到 WIN
      ↓
RZS
      ↓
反推出 relevance zone
      ↓
縮小 must-play region
      ↓
繼續搜尋
```

可以把它理解成：

> **NN 解決 move ordering；RZS 解決 proof pruning。**

這兩件事其實完全不同。

這也是為什麼作者強調，RZS 可以很自然地嵌入 AlphaZero、MCTS、alpha-beta 等搜尋，而不像傳統 lambda search 必須事前特別插入 null move。([arXiv][1])

---

# 14. 數量級差異可能非常驚人

假設一題平均：

```text
普通搜尋：

depth ≈ 15
branch ≈ 100
```

粗略就是：

$$
100^{15}
$$

當然真正 solver 有大量 pruning，不會真的這麼誇張。

RZS 可能變成：

```text
第一層：200
找到 RZ
↓
15

下一層：
15
↓
7

下一層：
7
↓
3
```

因此 solution tree 可以小非常多。

原始 AAAI 研究的實驗中，19×19 專業死活題集共 106 題：

* 先前比較的 solver 解出 **11 題**
* AlphaZero + RZS 解出 **36 題**
* 再加入 FTL（Faster to Life）後解出 **68 題**

這不能單獨歸功於 RZS，因為模型與 FTL 等因素也一起影響結果，但可以看出這套「神經網路找強手 + RZS 做 proof pruning」的實際價值。([arXiv][1])

---

## 最值得抓住的核心

我會把 RZS 濃縮成這個循環：

$$
\boxed{
\text{搜一手}
\rightarrow
\text{證明活}
\rightarrow
\text{反推出 RZ}
\rightarrow
\text{發現哪些手是 null}
\rightarrow
\text{縮小 MustPlay}
\rightarrow
\text{再搜}
}
$$

而不是：

$$
\text{先猜 RZ} \rightarrow \text{只搜 RZ}
$$

這個差別非常關鍵。

甚至可以把它類比成人類解死活時的思路：

> 「如果你脫先，我這裡一手就活；所以你一定只能在這幾個要點裡應。這幾個我全部算完，就證明你沒辦法殺。」

**RZS 基本上就是把這種「你非得在這裡應」的圍棋推理，形式化成可被電腦驗證與遞迴傳播的搜尋演算法。**

如果你有興趣，我下一步可以直接用一個 **5×5 或 7×7 的實際死活棋形**，逐手畫出 `MustPlay = 20 → 6 → 2 → 0`、每一步的 RZ 怎麼 union/intersection；那會比只看公式直觀很多。

[1]: https://arxiv.org/abs/2112.02563 "A Novel Approach to Solving Goal-Achieving Problems for Board Games"
[2]: https://github.com/rockmanray/rzone?utm_source=chatgpt.com "GitHub - rockmanray/rzone: An implementation of RZS for the game of Go · GitHub"

