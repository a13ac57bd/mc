# RPG 模組包

Minecraft 1.20.1 / Forge 47.4.10 的 RPG 模組包。規格在 [`design/TECH_SPEC.md`](design/TECH_SPEC.md)。

| 資料夾 | 內容 |
|---|---|
| `design/` | 程式規格 |
| `RpgCore/` | 主模組 `rpgcore`（屬性、特性、掉落、鍛造、危險度、AI、地城、Boss、裂隙、城市、任務、世界事件、家園） |
| `pack/` | packwiz 模組包（目前是空清單）與自測伺服器的下載腳本 |

## 建置（Windows，`C:\mc`）
```
cd C:\mc\RpgCore
gradlew build                 # 產生 build\libs\rpgcore-0.1.0.jar（已 reobf）
gradlew runClient             # 開發用客戶端（只有原版＋rpgcore）
gradlew runGameTestServer     # 跑 GameTest
gradlew deployServer          # 放進 C:\mc\server\mods
```
需要 JDK 17（`gradle.properties` 指向 `C:/mc/jdk17`）。Gradle wrapper 是 9.2.1，第一次建置會下載 Forge 與 Minecraft。

## 不需要 Minecraft 的檢查
```
gradle -p RpgCore/logic test                     # 純邏輯的 JUnit 測試
python3 RpgCore/tools/lint.py path/to/ecj.jar    # rpgcore 類別之間的一致性（Eclipse 編譯器，org.eclipse.jdt:ecj）
python3 RpgCore/tools/gen_structures.py          # 重新產生古堡與 GameTest 的結構檔
```

## 目前狀態
`gradlew build` 成功（0 個警告），`runGameTestServer` 全部通過，古堡自然生成正常。還沒在用戶端與外部模組（TACZ、Iron's、Curios）一起測過，詳見 TECH_SPEC §20。
