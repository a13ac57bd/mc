# 模組包（packwiz）

空的 packwiz 模組包：Minecraft 1.20.1、Forge 47.4.10。模組清單、版本、雜湊由 packwiz 管理，可以重現。

## 加入模組
在這個資料夾執行（需要 [packwiz](https://packwiz.infra.link/)）。slug 以 Modrinth 頁面為準：

```
packwiz modrinth add timeless-and-classics-zero   # TACZ（槍）
packwiz modrinth add irons-spells-n-spellbooks    # Iron's Spells（法術）
packwiz modrinth add curios                       # 飾品欄
packwiz modrinth add geckolib                     # Iron's 與之後的 Boss 動畫
packwiz modrinth add caelus                       # Iron's 依賴
packwiz modrinth add playeranimator               # Iron's / Better Combat 依賴
packwiz modrinth add better-combat
packwiz modrinth add waystones
packwiz refresh
```

不再需要的模組（rpgcore 自己實作了）：
- Champions：精英改由 rpgcore 原生詞綴處理（`danger/Elites`）。
- Easy NPC：城市 NPC 改用 rpgcore 自己的 `rpgcore:npc`。

## 自測伺服器
```
powershell -ExecutionPolicy Bypass -File pack\tools\fetch-mods.ps1   # 依清單下載到 C:\mc\server\mods（檢查雜湊）
cd C:\mc\RpgCore
gradlew deployServer                                                 # 放入 rpgcore
```
伺服器啟動後用 `/rpgcore selftest` 檢查全模組包的生物是否都 ×5 且滿血。

## 預設設定
- rpgcore 啟動時把 `keepInventory` 設為開（死亡規則 #27）。
- 裝了 Apothic Attributes 時，rpgcore 會把它的 `attributeslib:crit_chance` 基礎值設為 0（暴擊由 rpgcore 管，#3）。
