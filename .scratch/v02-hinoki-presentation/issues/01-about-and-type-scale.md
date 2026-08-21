# 01: 關於與全殼字級 v0.2

**What to build:** 解題者打開關於，產品名「詰碁」最大，一句「圍棋死活訓練機」，版本 `v0.2` 最小、不是主色英雄字。畫面標題是「關於」。空畫面提示、左上「黑先殺白／輪黑／思考」、右側搜尋路徑用同一套字級。選單仍是 Import、File、Edit、About。搜尋路徑預設打開、紙墨窄欄、編號與條數契約不變。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 關於標題為「關於」，品名「詰碁」大於摘要，摘要大於 `v0.2`
- [x] 版本不是最大、也不是主色強調的英雄字
- [x] 空畫面提示與對局左上、搜尋路徑同一套字級
- [x] TopMenu 仍為 Import、File、Edit、About
- [x] 搜尋路徑仍從 1 編號，例如 `1. 白下 B3 -> 黑下 A3 -> 結果 成功`；列出實際條數
- [x] 產品版本常數顯示為 v0.2

## Answer

`aboutTitle()` 為「關於」；`AppVersion.DISPLAY` 為 `v0.2`。關於用 displaySmall 品名、bodyLarge 摘要、labelSmall 版本。搜尋路徑紙墨欄、bodyLarge 編號不變。選單仍 About。
