# 排班系統：交接文件

> 給「在自己電腦上用 Claude Code 繼續開發」的人（或 Claude）看。
> 寫於 2026-11-23。程式在 GitHub `a13ac57bd/mc` 的 `ccr-cf58de7c-bbqsj8` 分支，位於 `paiban/` 資料夾。

---

## 1. 把程式拿到本機

`mc` repo 本身是 Minecraft 模組專案，排班系統只是暫時放在裡面。**建議把 `paiban/` 獨立成自己的資料夾**：

```bat
:: 在 Windows 命令提示字元（需要先安裝 Git）
cd C:\
git clone -b ccr-cf58de7c-bbqsj8 https://github.com/a13ac57bd/mc.git mc-temp
xcopy /E /I mc-temp\paiban C:\paiban
rmdir /S /Q mc-temp
cd C:\paiban
git init
git add .
git commit -m "排班系統初始版本"
```

不想裝 Git 的話：在 GitHub 網頁切到 `ccr-cf58de7c-bbqsj8` 分支，按 Code → Download ZIP，解壓後只取 `paiban` 資料夾。

然後在 `C:\paiban` 開啟 Claude Code（`claude`），它會自動讀到同資料夾的 `CLAUDE.md`。

---

## 2. 需求（已和使用者確認）

| 項目 | 決定 |
|---|---|
| 使用者 | 約 100 名員工＋管理員 |
| 使用地點 | **只在公司一台 Windows 電腦上使用**（大家輪流用同一台）。不開放手機、不上網、不花錢 |
| 帳號 | 員工編號（測試用 `001`～`100`）＋ `admin`。編號只有本人知道，不用擔心被冒用 |
| 密碼 | **第一次登入輸入的密碼即成為密碼**，之後要輸入相同密碼。忘記密碼由管理員重設 |
| 員工填寫 | **下下週**（週一～週日）7 天 × 24 小時，勾選「願意上班」的時段 |
| 填寫截止 | 本週日 23:59。週一 00:00 自動換成下一週，前一週就不能再改 |
| 員工查看 | **下週**（和本週）的班表，管理員按「公布」後才看得到 |
| 排班方式 | **目前管理員手動排**。自動排班是之後的目標，尚未討論規則 |
| 一週開始 | 週一 |

---

## 3. 技術架構

- **Python 3 標準函式庫**，不需要 `pip install` 任何套件（`http.server` + `sqlite3` + `hashlib.scrypt`）
- **SQLite**：資料庫為同資料夾的 `paiban.db`（第一次啟動自動建立，已列入 `.gitignore`）
- **前端**：純 HTML/CSS/JS，沒有框架、沒有建置步驟
- 伺服器預設只綁 `127.0.0.1:8080`（只有本機能連）

| 檔案 | 內容 |
|---|---|
| `server.py` | 全部後端：資料表建立、登入、API、靜態檔案 |
| `static/index.html` | 頁面骨架、登入表單 |
| `static/app.js` | 全部前端邏輯（員工填寫、班表查看、管理員排班、員工管理、閒置登出） |
| `static/style.css` | 樣式（含手機版） |
| `start.bat` | 啟動伺服器並打開瀏覽器 |
| `backup.py` / `backup.bat` | 線上備份資料庫到 `backups\`，保留 60 份 |
| `README.md` | 給使用者看的安裝與操作說明 |

### 資料模型（`server.py` 的 `init_db`）

- `users(id, name, pw_hash, is_admin, active)`：`pw_hash` 為 NULL 代表「下次登入的密碼即成為密碼」
- `sessions(token, user_id, expires)`
- `availability(user_id, week_start, day, hours)`：員工填的可上時段
- `availability_meta(user_id, week_start, updated_at)`：最後儲存時間（用來判斷有沒有填）
- `schedule(user_id, week_start, day, hours)`：管理員排定的班
- `weeks(week_start, published_at)`：`published_at` 為 NULL 是草稿，員工看不到

**時段的表示法**：每天一個整數 `hours`，24 個位元，第 h 位元 = h:00～h+1:00。全天 = `(1<<24)-1`。`week_start` 一律是週一的 `YYYY-MM-DD`，`day` 0=週一…6=週日。前後端都用這個格式。

### API

| 方法 | 路徑 | 權限 | 說明 |
|---|---|---|---|
| POST | `/api/login` | 無 | `{id, password}`；首次登入會設定密碼，回傳 `first: true` |
| POST | `/api/logout` | 無 | |
| GET | `/api/me` | 無 | 目前使用者、本週／下週／填寫週的日期 |
| POST | `/api/password` | 登入 | `{old, new}` 改密碼 |
| GET/PUT | `/api/availability` | 登入 | 讀寫「下下週」的可上時段；PUT 的 `week_start` 不是目前填寫週會回 409 |
| GET | `/api/schedule?week=` | 登入 | 員工只能看本週、下週，且要已公布 |
| GET | `/api/admin/week?week=` | 管理員 | 該週所有員工的 avail＋sched＋公布狀態 |
| PUT | `/api/admin/schedule` | 管理員 | `{week_start, sched: {id: [7 ints]}, publish?: bool}` |
| GET | `/api/admin/users` | 管理員 | |
| POST | `/api/admin/user` | 管理員 | 新增或修改 `{id, name, active}` |
| POST | `/api/admin/reset-password` | 管理員 | `{id}`，把 `pw_hash` 清成 NULL |

### 安全相關的設計（共用電腦）

- 密碼用 scrypt 雜湊；登入失敗同帳號或同 IP 10 分鐘 10 次就暫停
- Session cookie：`HttpOnly; SameSite=Strict`，**不設 Max-Age**（關瀏覽器即登出），伺服器端最多 12 小時
- 前端閒置自動登出：員工 2 分鐘、管理員 30 分鐘，最後 30 秒顯示紅色倒數（`app.js` 的 `idleLimit`）。**未儲存的修改會遺失**
- 登入表單 `autocomplete="off"`／`new-password`，避免瀏覽器記住密碼
- POST/PUT 只收 `application/json`（空 body 例外），配合 SameSite 防 CSRF
- 所有顯示到畫面的使用者文字都經過 `esc()`

---

## 4. 在本機執行與測試

```bat
cd C:\paiban
python server.py --open          :: 或雙擊 start.bat
```

- 想用另一個資料庫測試、不弄髒正式資料：`set PAIBAN_DB=C:\temp\test.db` 再啟動
- 換 port：`python server.py --port 8099`
- 開放區網連線（目前不需要）：`--host 0.0.0.0`

**目前沒有自動化測試**。開發時是用 curl 打 API、用 Playwright 跑瀏覽器流程手動驗證的。每次修改後建議至少手動走一遍：

1. 用 `admin` 首次登入設密碼
2. 用 `001` 首次登入 → 填幾個時段 →「儲存並登出」→ 再登入確認有存到
3. 輸入錯誤密碼 → 顯示「編號或密碼錯誤」
4. `admin` → 排班頁按 ▶ 到「下下週」→ 看得到 001 填的淺綠色 → 點格子排班 → 「儲存並公布」
5. 用 ◀ 回到「下週」排班並公布 → `001` 登入「我的班表」→「下週」看得到
6. `admin` → 員工管理 → 重設 `001` 密碼 → `001` 用新密碼登入成功
7. 登入後放著不動 2 分鐘 → 出現倒數 → 自動回到登入頁
8. 按「登出」後重新整理，確認仍是登入頁

---

## 5. 已知限制與注意事項

- 「下下週」在週一 00:00 依**伺服器電腦的時間**切換，電腦時間要正確
- 管理員儲存排班時會把畫面上**所有啟用員工**當週的 sched 整批送出；兩個管理員同時編輯同一週，後存的會覆蓋先存的（目前只有一個管理員，問題不大）
- 停用的員工不會出現在排班表，但他們已有的 schedule 資料會保留
- `admin` 帳號在第一次啟動時就存在、沒有密碼；**部署後要馬上登入設定密碼**
- 排班頁一次只顯示一天（員工 × 24 小時），沒有整週總覽
- 管理員「排班頁」的 ◀ ▶ 可以去任何週次，包含過去的週

---

## 6. 接下來可以做的事（依使用者意願排序）

1. **自動排班**（使用者的主要目標，規則還沒討論）。需要先跟使用者確認：
   - 每個時段最少／最多幾人？不同時段或星期是否不同？
   - 每人每週時數上下限？每天最多幾小時？兩班之間最少休息幾小時？
   - 是否要公平分配時數？員工有沒有優先順序或技能差異？
   - 自動排完後應讓管理員再手動微調（目前的手動畫面可以直接沿用）
2. 閒置自動登出前**自動幫員工儲存**（目前是直接丟掉未儲存的修改，已向使用者提過，尚未決定）
3. 匯出班表（Excel／CSV、列印用版面）
4. 管理員的整週總覽、未填寫名單
5. 加入自動化測試（Python `unittest` 打 API 即可，不需要額外套件）
6. 若日後要讓手機也能連：`--host 0.0.0.0`＋ Windows 網路設為「私人」；要從家裡連則需要 Cloudflare Tunnel 和一個網域（使用者目前**不想花錢**，先不做）
