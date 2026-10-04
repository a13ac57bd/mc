# 程式規格
依據：MECHANICS.md。標記同 DECISIONS.md。外部模組 API 的名稱是依我的記憶寫的，**M0 必須逐一驗證**，不符合就改這份文件。

> **2026-10 從零重建**（這個 repo）：計畫變更，舊的 `C:\mc` 成果不沿用，rpgcore 在這裡從零寫起，涵蓋 M0–M10 的程式框架。
> 和原規格不同的地方都在下面各節用「**重建：**」標出。驗證狀態見 §20。

---

## 1. 環境
| 項目 | 值 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | **47.4.10**（1.20.1 recommended 版） |
| 建置 | **ModDevGradle legacyforge 2.0.147**（Gradle 9.2.1，和其他專案共用快取）、Parchment 1.20.1 2023.09.03、Mixin 0.8.5。M0 改：不用 ForgeGradle 6，避免另外一套 Gradle 8 |
| Java | **JDK 17**（`C:\mc\jdk17`，Gradle 工具鏈）；Gradle 本身用 jdk21 跑 |
| 自測伺服器 | `C:\mc\server`：真的 Forge 伺服器，模組由 `pack\tools\fetch-mods.ps1` 依 packwiz 清單下載（雜湊檢查），rpgcore 用 `gradlew deployServer` 放進去 |
| Gradle 快取 | `C:\mc\.gradle-home`（`$env:GRADLE_USER_HOME`） |
| 外部模組依賴 | **重建：**不放進建置。TACZ、Iron's、Curios 一律在 `compat/` 用反射綁定（`Ref.listen` 依類別名稱訂閱事件），建置只需要 Forge。名稱不符時記錄警告並關掉該整合，不會崩潰 |
| 模組包 | `C:\mc\pack`，用 **packwiz** 管理（模組清單、版本、雜湊可以重現） |

## 2. 專案配置
```
C:\mc\
 ├─ design\              規格（本文件、MECHANICS、DECISIONS…）
 ├─ RpgCore\             自製主模組 modid=rpgcore，package com.rpgcore
 │   ├─ logic\           只建置 com.rpgcore.logic 的獨立 Gradle 專案（不需 Minecraft，跑 JUnit）
 │   └─ tools\           lint.py（Eclipse 編譯器跨檔檢查）、gen_structures.py（產生結構 NBT）
 ├─ VillagesAndRoads\    移植到 Forge 1.20.1（M6）——**重建：未包含**，原始碼不在這個 repo
 ├─ aibuildbridge\       移植到 Forge 1.20.1（M6、M9）——**重建：未包含**，見 §16、§17
 ├─ pack\                packwiz 模組包（重建：空清單＋fetch-mods.ps1，見 pack/README.md）
 └─ jdk17\ jdk21\ .gradle-home\ _jars\dev\（.gitignore 排除）
```

## 3. rpgcore 套件結構
```
com.rpgcore
 ├─ RpgCore                 進入點、註冊
 ├─ registry/               物品、方塊、方塊實體、屬性、選單、音效、維度
 ├─ stat/                   8 種屬性（§MECH 2）、屬性註冊與套用
 ├─ item/                   ItemData（NBT 讀寫）、稀有度、階級、說明框
 ├─ trait/                  特性引擎
 │   ├─ TraitDef            資料定義（JSON codec）
 │   ├─ trigger/            Trigger 列舉＋事件來源
 │   ├─ condition/          Condition 註冊表（codec）
 │   ├─ effect/             Effect 註冊表（codec）
 │   ├─ TraitCache          玩家目前生效特性的快取，換裝時重算
 │   └─ TraitStacks         疊層、冷卻（玩家 Capability）
 ├─ loot/                   掉落來源、保底、自動拆解、GlobalLootModifier
 ├─ forge/                  鍛造台：方塊、選單、升級、重洗、銘刻、拆解
 ├─ danger/                 危險度計算、區塊快取、怪物生成時套用、Champions 介接
 ├─ combat/                 Dash、數值尺度 ×5、生命條 HUD、死亡規則
 ├─ dungeon/                捷徑門、秘密牆、陷阱、誓約石、地城實例存檔、環境機關（炸藥桶、吊燈、油地）
 ├─ ai/                     角色分工 Goal（衝鋒、射手、坦克、包抄、支援）、怪群組隊
 ├─ ecology/                陣營敵對、日夜／區域生怪表、腐化變異
 ├─ town/                   城市據點：服務 NPC、旅館重生、城市狀態
 ├─ quest/                  手工任務線、日誌、線索、導航開關、主線章節與選擇
 ├─ boss/                   自製 Boss 共用框架：招式表、階段、場地機制
 ├─ events/                 世界事件（MECH §21）
 ├─ rift/                   裂隙（MECH §22）
 ├─ home/                   建築台、藍圖、建材解鎖、獎盃
 ├─ compat/                 TaczCompat、IronsCompat、CuriosCompat（反射，ModList 檢查後才綁定）
 ├─ logic/                  純邏輯（不得 import net.minecraft）：稀有度、流派、觸發、跨流派驗證、保底、
 │                          鍛造花費、危險度、Dash 物理、裂隙數值、世界事件狀態機、任務流程、結局表
 ├─ data/                   RpgData：單一 reload listener 讀取 data/<ns>/rpgcore/** 全部 JSON
 ├─ command/                /rpgcore 指令
 ├─ net/                    SimpleChannel 封包：特性快取同步、HUD、鍛造動作
 ├─ client/                 HUD（冷卻、疊層）、說明框、稀有度邊框、掉落光效
 └─ test/                   GameTest、伺服器自測
```

## 4. 物品資料（NBT）
1.20.1 沒有資料元件，用物品 NBT 的 `rpgcore` 子標籤：
```json
"rpgcore": {
  "rarity": "legendary",          // common|uncommon|rare|legendary|artifact
  "tier": 3,                      // 1–5
  "upgrade": 1,                   // 鍛造升階次數
  "unique": "rpgcore:void_hunter",// 傳奇／神器的定義 id，可以沒有
  "traits": [ {"id":"rpgcore:burning_bonus","roll":0.15,"locked":false} ],
  "inscription": "rpgcore:frost_core", // 銘刻，可以沒有
  "rerolls": 2
}
```
- 傳奇與神器的定義：`data/<ns>/rpgcore/uniques/*.json`，內容是基底物品、稀有度、固定特性、所屬掉落池。
- TACZ 的槍是同一個物品 ID 加上 NBT 的 GunId，所以傳奇槍＝指定 GunId＋rpgcore 標籤。Hop-up 是 TACZ 的配件物品加上 rpgcore 標籤。

## 5. 特性引擎（M2 完成，`trait/`）
### 5.1 資料格式 `data/<ns>/rpgcore/traits/*.json`
一條特性可以有多條規則（rules），每條規則一個觸發。例：虛空獵槍
```json
{
  "type": "unique",                        // minor|unique
  "school": "gun",                         // gun|spell|melee|bow|any
  "roll": {"min":0.10,"max":0.20},         // 只有小特性需要；數值參數可以寫 "roll" 取用物品擲出的值
  "tooltip": "trait.rpgcore.void_hunter",  // 省略時 = trait.<ns>.<path>，%s = roll 的百分比
  "rules": [
    {"trigger": "gun_hit", "effects": [{"type": "damage_mult_per_hit", "per": 0.25}]},
    {"trigger": "gun_hit", "conditions": [{"type": "distance", "max": 8}], "effects": [{"type": "damage_mult", "value": 0.6}]}
  ]
}
```
- 條件：`distance`、`hit_index`（穿透序）、`pellets`（同一發打到同一目標的彈丸數，剛好等於才觸發）、`counter`、`flag`、`not_flag`、`spell`（法術 id 清單）、`spell_school`、`target_frozen`、`crit`、`headshot`、`chance`。
- 效果：`damage_mult`、`damage_mult_per_hit`、`force_crit`、`force_headshot`、`set_flag`／`clear_flag`、`counter_add`（一段時間沒加就歸零）、`ignite`、`knockback`、`stun`（rpgcore:stun 效果）、`aoe_damage`、`unfreeze`、`heal_fraction`、`attribute`（裝備時常駐）、`temp_attribute`；compat 註冊 `refill_magazine`（TACZ）、`extra_casts`（Iron's）。
- 旗標、計數器的名稱一律加上特性 id 當前綴，所以**不同特性之間無法互傳狀態**（#3 的另一道保險）。
- 傳奇／神器定義：`data/<ns>/rpgcore/uniques/*.json`：`item`、`nbt`（SNBT，TACZ 槍寫 `{GunId:"rpg:..."}`）、`rarity`、`traits`、`pool`。`/rpgcore unique <id>` 取得。名稱 `unique.<ns>.<path>`、說明 `.desc`。

### 5.2 觸發（M0 驗證 API，M2 實作）
`*_hit` 在傷害確定前執行，可以改傷害、強制暴擊或爆頭；`*_dealt` 在傷害確定後執行，看得到最終數字（回血、點燃、爆炸、暈眩用這個）。
| Trigger | 來源 |
|---|---|
| melee_hit／melee_dealt | `LivingHurtEvent`（`combat/DamageRules`，玩家直接攻擊） |
| bow_hit／bow_dealt | 同上，直接傷害來源是箭矢 |
| gun_hit | TACZ `EntityHurtByGunEvent.Pre`（改子彈基礎傷害、爆頭）；穿透序 = 同一顆子彈第幾次命中 |
| gun_dealt | TACZ `EntityHurtByGunEvent.Post`；彈丸數 = 同一 tick 打到同一目標的次數 |
| gun_shoot／gun_kill／gun_reload | TACZ `GunFireEvent`／`EntityKillByGunEvent`／`GunReloadEvent` |
| spell_cast | Iron's `SpellOnCastEvent` |
| spell_hit／spell_dealt | Iron's `SpellDamageEvent`（前改傷害、後看結果） |
| dash | rpgcore `DashEvent` |
| kill、hurt | 原版 `LivingDeathEvent`、`LivingHurtEvent`（玩家受傷） |
| passive | 裝備中常駐，只能放 `attribute` 效果 |

### 5.3 執行
1. 換裝時（`LivingEquipmentChangeEvent`、Curios `CurioChangeEvent`、登入、重生）重算 `TraitCache`：計入主手（武器）、四個護甲欄、Curios 欄位；依觸發分組。常駐屬性在這時加上或移除。用戶端說明框直接讀物品 NBT，不需要同步。
2. 事件發生時只查該觸發的清單，沒裝特性的玩家幾乎沒有開銷。
3. **跨流派過濾【#3】在載入時強制**：特性的觸發必須符合它的 school（gun 只能用 gun_* 和通用觸發，spell 只能用 spell_*，melee／bow 可以互通【#20】）；any 也不能同時用槍和法術的觸發。違反的特性被拒絕載入並記錄錯誤。
4. 遞迴保護：特性效果造成的傷害在 `TraitEngine.nested` 裡執行；巢狀中，命中類觸發只跑標了 `"recursive": true` 的規則，深度上限 2。
5. 法術變異：`extra_casts` 在下一個 tick 暫時轉動施法者朝向，直接呼叫 `AbstractSpell.onCast(...)`（不扣法力、不進冷卻）；傷害倍率用 `spell_hit` 規則的 `damage_mult`。
6. 狀態（旗標、計數器、暫時屬性）存在記憶體裡，登出就清掉，只用於秒級的戰鬥狀態。
7. 注意：Iron's 的法術流派來自它的設定，在**資料包同步**（玩家第一次加入或 /reload）時才載入；在那之前所有法術都回報預設流派。
## 6. 掉落與鍛造（M3 完成，`loot/`、`forge/`）
- **一個** GlobalLootModifier `rpgcore:rpg_loot` 處理全部：玩家殺死的生物 → `mob`／`elite`（Champions 的精英）／`boss`（實體標籤 `#rpgcore:boss`）；玩家打開的 `chests/*` 戰利品表 → `chest`。原本的掉落保留，rpgcore 的另外加上去。
- 來源定義 `data/<ns>/rpgcore/loot_sources/*.json`：固定掉落（`#material` = 依階級的怪物素材、`#ammo` = 隨機 rpg 彈藥）、裝備機率與稀有度分布、傳奇機率／池／首殺必出。數值照 MECHANICS §5（#37）。
- 階級（tier 1–5）＝ 掉落位置的危險度；M3 先用「距離出生點」的環狀分級（`Sources.tier`），M4 換成完整的危險度。
- 裝備基底 `data/<ns>/rpgcore/bases/*.json`：近戰、弓、槍、護甲、飾品五類，每個基底有可出現的階級範圍（#46）。產生時：稀有度決定小特性數量（0／1／2，#23），同一件裝備的特性屬於同一流派，擲出的數值 × 階級倍率。
- 階級倍率（#45）：1 + 0.15 ×（階級 − 1）。近戰武器和護甲用 `ItemAttributeModifierEvent` 放大物品自己的攻擊、護甲、韌性；弓和槍在傷害計算時乘上手上武器的階級。
- 傳奇：從來源指定的池（`uniques` 的 `pool`）抽，基底物品沒裝的模組自動跳過；50% 再加一條同流派小特性（#23 的 0–1 條）。保底（#48）：機率 = 基礎 ×（1 + 0.1 × 連續沒出次數），每個來源分開計數；Boss 首殺必出（每位玩家每種 Boss 一次）。
- 玩家狀態放在 Forge 的 `PlayerPersisted`（死亡不會清掉）：保底計數、已擊殺的 Boss、自動拆解門檻。
- 自動拆解：撿起時（`EntityItemPickupEvent`）稀有度不高於門檻（關／普通／精良／稀有）且不是傳奇，就直接換成素材。門檻在鍛造台的「拆解」分頁切換。
- 鍛造台（`rpgcore:forge_table`，配方：鐵錠＋鍛造台）：一個裝備格＋背包，四個分頁，花費從背包扣（`forge/ForgeOps`，純邏輯可測）：
  - 升級：階級 +1，所有特性數值跟著放大
  - 重洗：只換一條非固定的特性（同流派），其他不動；只限稀有以上
  - 銘刻：特殊素材 → 對應的固定銘刻特性（#47），`data/<ns>/rpgcore/inscriptions/*.json`
  - 拆解：換成素材
- 介面暫時用純色繪製，等美術的 176×166 貼圖（ART_SPEC 1.8）。
- 特性系統補充：`type` 可以是 `minor`、`unique`、`inscription`；新觸發 `any_hit`（任何流派的命中，只能用沒有狀態的效果，所以不會把一個流派的命中帶到另一個流派）。
- 待辦：TACZ 預設槍包的槍還能在槍械工作台合成；它的配方篩選只影響顯示。要整理預設槍包（我們的佔位模型還在用它的模型）時一起處理。
## 7. 危險度與精英（M4 完成，`danger/`、`compat/champions`）
- `DangerMap.get(level, pos)`：距離出生點的環狀分級（1000／2500／5000／8000）＋生態域修正 → 結構覆蓋（固定值）→ 世界事件修正（`eventModifier`，M7 用），夾在 1–5。距離＋生態域以區塊為單位快取；結構只在已生成的區塊查。設定在 `data/<ns>/rpgcore/danger/config.json`（環的距離、維度固定值、生態域、結構）。掉落的階級也用它（取代 M3 的暫代）。
- `MobScaling`：敵對生物在 `MobSpawnEvent.FinalizeSpawn` 依危險度：生命每級 +35%（MULTIPLY_TOTAL 修正）、傷害每級 +25%（在 `LivingHurtEvent` 乘，箭矢算射手的）；危險度存在生物身上。Boss 和事件怪（`rpgcore_event_mob` 標記）不套用。
  - 注意：Forge 的 FinalizeSpawn 事件由「生成的呼叫端」發出（`ForgeEventFactory.onFinalizeSpawn`），直接呼叫 `Mob#finalizeSpawn` 不會觸發；rpgcore 自己生成生物時一律用 Forge 的版本。
- **重建：精英改為 rpgcore 原生**（`danger/Elites`）：加入世界時依危險度擲 2–10%，詞綴 1／1／2／2／3 條，從 強韌（生命 +50%）、迅捷、殘暴（傷害 +40%）、重甲、吸血、熔岩（火免、近戰攻擊者起火）、巨力（擊退）抽。判斷「是精英」＝ 有詞綴。不再需要 Champions。以下為舊方案紀錄：
- 精英（#26 #38）：**Champions-Unofficial 20.1.1.6 的 Forge 版自己永遠不會產生精英**（它的資格檢查用 `isChampion()`，而這個對所有生物都回傳 true）。所以由 rpgcore 在敵對生物加入世界時依危險度擲機率（2–10%），再呼叫 Champions 的建構器（內部類別 `ChampionsRegistries.builder()`）把它變成精英，並在 Champions 的 SPAWN 回呼裡把詞綴數調成 1／1／2／2／3。Champions 自己的 `spawnChance` 設 0、它的掉落關掉（模組包 `defaultconfigs/champions-server.toml`），精英的掉落由 rpgcore 處理。判斷「是精英」＝ 有 Champions 詞綴。
- 測試時（自測的 GameTest）關掉自然精英擲骰（`Elites.suppressed`），避免隨機精英改變傷害數字。
## 8. 戰鬥
- 不使用 Combat Roll【定 #18】。
- Dash【定 #19】（M1 完成，`combat/Dash`）：rpgcore 自己的按鍵（預設左 Alt），所有玩家都有。用戶端只送「按下＋移動輸入」，伺服器檢查充能、地面後設定速度（`hurtMarked` 同步）；基礎沒有無敵幀。初速 = 距離 ×（1 − 摩擦），所以不管地面材質都剛好走 4 格。充能一格一格回復。充能數、冷卻、距離、可否空中使用都是玩家屬性（`rpgcore:dash_charges`、`dash_cooldown`、`dash_distance`、`dash_air`），特性可以修改。成功時發出 `DashEvent`（特性觸發 dash）。
- 數值尺度 ×5【定 #16、#17】（M1 完成，`combat/Scaling`）：**所有計算都用原版單位，只有最後扣血或補血時 ×5**。
  - 玩家最大生命基礎值設 100；其他生物在 `EntityJoinLevelEvent` 加 MULTIPLY_TOTAL ×5 修正（固定 UUID，不重複套用），套用前是滿血就補滿。
  - 傷害在 `LivingDamageEvent`（LOWEST）×5，也就是在護甲、附魔、吸收之後。原因：原版護甲公式和傷害絕對值有關，如果在 `LivingHurtEvent` 先 ×5，護甲會變得幾乎沒用。
  - 補血在 `LivingHealEvent` ×5（自然回復、藥水、法術治療的手感不變）。吸收值維持原版單位，HUD 顯示時 ×5。
  - 例外用標籤：傷害類型 `#rpgcore:no_scale`（目前只有 `generic_kill`）、生物 `#rpgcore:no_health_scale`（盔甲座）。
  - 開服自測檢查全模組包 240 種生物生成後都是 ×5 且滿血（M0 報告 §7 擔心的「生成時自己設血量」都沒問題）。
- 流派傷害（M1 完成，`combat/DamageRules`）：依傷害來源分類：法術（`#rpgcore:spell`，Iron's 的傷害類型）、槍（`#rpgcore:gun` 或子彈實體 `#rpgcore:gun_projectile`）、弓（箭矢實體）、近戰（玩家直接攻擊）。近戰 × `melee_power`、弓 × `ranged_power`、槍在 TACZ `EntityHurtByGunEvent.Pre` 改子彈基礎傷害 × `ranged_power`；暴擊（`rpgcore:crit_chance`、`crit_damage`）只對近戰、弓、槍。法術什麼都不加（#3），由 Iron's 的 spell power 處理。
  - Apothic Attributes 的暴擊會套用到所有有攻擊者的傷害（包含法術，違反 #3），所以 rpgcore 把它的 `attributeslib:crit_chance` 基礎值設 0，暴擊由 rpgcore 自己管。
- HUD（M1 完成，`client/Hud`）：原版愛心改成生命條＋數字（取消原版 `player_health` overlay）；吸收顯示成黃色細條和「+N」。Dash 充能沒滿時在準星下方顯示充能格。
- 槍包（M1 骨架）：`C:\mc\pack\tacz\rpg_apex`（命名空間 `rpg`），8 把槍＋5 種彈藥（ART_SPEC 第 2 批），模型、動畫、音效暫時指向 TACZ 預設槍（佔位）。後座改成固定值（每發都一樣，可以學）。TACZ 的資料格式只有「單發後座曲線」，Apex 那種「整個彈匣的後座圖形」需要之後在用戶端掛鉤做。TACZ 預設的寫實槍包還在，M3 做掉落時再從掉落和合成裡拿掉。
- 死亡規則【#27】（M3 完成，`combat/Death`）：伺服器啟動時把遊戲規則 keepInventory 設為開（背包、護甲、Curios 都保留，也不會掉經驗球）；`PlayerEvent.Clone` 時只給新玩家原本經驗總量的一半。

## 9. 地城（M5 完成，`dungeon/`）
- 結構：原版 Jigsaw（`structure`、`template_pool` 資料檔）。固定骨架用單一池，隨機房間用多選項池。
  - 古堡樣板 `rpgcore:castle`：入口 → 走廊（2 選 1）→ 樞紐 → 左右 2 條支線（從寶藏／陷阱／死路抽）＋誓約廳 → Boss 房（32×12×32）。Jigsaw 名稱 `rpgcore:door_out` 接 `rpgcore:door_in`。結構組 spacing 24、separation 8，生態域標籤 `#rpgcore:has_structure/castle`（森林、針葉林、平原、草甸、莽原），危險度固定 3【提案】。
  - 房間 NBT 目前由程式產生（佔位，石磚＋原版方塊），美術階段在遊戲中重蓋存檔。
  - 所有地城結構放進標籤 `#rpgcore:dungeon`，地城實例的範圍盒取自結構。
- 方塊（不可破壞，生存模式）：
  - 捷徑門 `shortcut_door`：FACING 指向「內側」，只能從內側開；開啟狀態存在方塊狀態裡（隨世界保存），上下相連的門一起開
  - 秘密牆 `secret_wall`：方塊實體記錄條件（預設打 3 下，或持特定物品），揭開時連帶最多 64 格相連的秘密牆
  - 陷阱：原版發射器 → 紅石粉 → 壓力板（箭矢不能在目標碰撞箱內生成，否則打不到）
  - 誓約石 `oath_stone`：右鍵啟動，記錄玩家
- `DungeonData`（SavedData）：地城實例的範圍盒（結構範圍，沒有結構時取誓約石周圍 48 格）、誓約石位置、已啟動的玩家。
- 重生：`PlayerRespawnEvent` 時，如果死亡點在已啟動的地城範圍內，傳送到誓約石。
- 房間規格：出入口用 Jigsaw 方塊標記。
- 開服自測：找最近的古堡，生成它範圍內所有區塊，統計 rpgcore 方塊、殘留 Jigsaw、箱子、發射器，輸出 `castle_top.png`、`castle_side.png`。

## 10. 世界事件（框架）
- `EventDef` JSON：
  - 狀態列表
  - 每個狀態的持續天數和下一個狀態
  - 每個狀態的效果：危險度修正、生怪表、NPC 開關、事件 Boss、地形替換
  - 玩家介入的目標與分支
- `WorldEventSavedData`：每個城市或區域的目前狀態、進度，每個遊戲日推進。
- 「不跳通知」的提示方式：由狀態效果產生 NPC 對話、遠方煙柱粒子、難民實體。
- 觸發【定 #14】：`EventDef.trigger` 只接受進度條件：
  - `boss_killed`
  - `chapter_done`
  - `location_discovered`
  - `quest_done`
  - 由 `ProgressSavedData`（世界層級的進度旗標）發出
- 觸發後由每日 tick 推進；同時進行上限 2 個，多的放進佇列。
- 結局分「守住」和「陷落後收復」，獎勵表分開。

## 11. 裂隙【定 #15】（M8 完成，`rift/`）
- 實作摘要：
  - 維度 `rpgcore:rift`（虛空平坦、固定午夜、無天光）。每個副本占一個 1024 格的格子，房間在 y=100，半徑 10（佔位，美術階段換成房間池）。用完拆掉並取消強制載入。
  - 參數都在 `data/<ns>/rpgcore/rift/config.json`：秒數、每層強度、每層獎勵、Boss 起始層與機率、怪物清單（4 隻起、每層 +1）、入口機率與距離。
  - 怪物用 `MobScaling.apply(怪, 副本階級, 層數強度)`；副本範圍內的危險度用 `DangerMap.overrides` 固定成副本階級。
  - 清空一層 → 獎勵（掉落來源 `rift`）累積 → 出現「繼續下潛」與「離開」兩個門方塊。離開＝拿全部獎勵；超時或全員死亡＝獎勵減半（每疊減半、單件隔一件留一件）【提案】，傳回進入點。
  - 剛強制載入的區塊裡新加的實體，下一個 tick 前用 UUID 找不到，所以副本直接持有怪物實體的參照。
  - 入口：入口實體 `rift_portal`（只有粒子），自然入口在天亮時消失；信標物品 `rift_beacon`（精英 0.5% 掉落）在原地開入口，蹲下使用＝當地 +1。
- 維度 `rpgcore:rift`，每層是一個由房間池生成的小型副本，配置在區域格上，用完回收。
- `RiftInstance`：
  - 計時（10 分鐘）
  - 層數
  - 強度：每層 +15%
  - 獎勵倍率：每層 +20%
  - Boss 機率：第 5 層以後 20%
  - 超時處理：獎勵減半並傳出
- 隨機入口：
  - 伺服器每天替每位玩家擲骰，平均每 3 天一次
  - 入夜時在玩家 50–150 格內找合法位置，放置入口實體
  - 天亮時移除
- 信標物品：在原地生成入口實體，強度可選「當地」或「當地 +1」。
- 測試指令：`/rpgcore rift open [tier]`。

## 12. 怪物 AI 與生態（M4 完成，`ai/`、`ecology/`）【定 #8 #10】
- 角色（實體標籤 `#rpgcore:role/<role>`，加入世界時加上目標）：
  - 衝鋒手 `ChargerGoal`：4–14 格時直線衝刺（速度 ×1.8，最多 2 秒），打到就後撤 1 秒，冷卻 4 秒
  - 射手 `KiteGoal(10, 16)`：目標進到 10 格內就往遠處退
  - 坦克 `ShieldWallGoal`：站在目標和最近的射手／支援隊友之間（隊友前方 3 格），面向目標；正面 60° 內受到的傷害 ×0.5
  - 包抄手 `FlankGoal`：先繞到目標側面偏後 4 格，到了再交給原本的近戰
  - 支援 `KiteGoal(8, 14)`＋`SupportGoal`：每 8 秒治療 12 格內最虛弱的隊友（再生 II 5 秒），沒人受傷就給戰鬥中的隊友力量 8 秒
  - 目前的分配：衝鋒＝殭屍、殭屍村民、溺屍、衛道士；射手＝骷髏、流髑、掠奪者；坦克＝屍殼、劫毀獸；包抄＝蜘蛛、洞穴蜘蛛；支援＝女巫。現成模組的 Boss 和怪物不加（保留原本 AI）。
  - 所有 rpgcore 目標繼承 `TimedGoal`，自測會量出 rpgcore 自己的 AI 時間。注意：沒有 `requiresUpdateEveryTick` 的目標每兩個 tick 才執行一次，不能用 `tickCount % n` 做週期。
- 陣營 `data/<ns>/rpgcore/factions/*.json`＋實體標籤 `#rpgcore:faction/*`：亡靈、野獸、盜匪、腐化（腐化的成員等自製怪物）。互相敵對的陣營成員會互相攻擊（目標選擇器 priority 3）；同陣營是隊友。
- 小隊 `data/<ns>/rpgcore/squads/*.json`：自然生成的隊長依機率帶出隊員（例如殭屍＋屍殼＋2 骷髏），隊員用同一個危險度；任何一員找到目標，32 格內閒著的隊友跟上（有防重入，事件在目標設定前觸發）。
- 生怪表 `data/<ns>/rpgcore/spawns/*.json`：依維度、危險度範圍、白天／夜晚，`add` 加進原本的清單，`replace` 換掉（等世界生成實測、區域主題確定後再用）。執行中可加表（世界事件用 `SpawnTables.extra`）。
- 腐化：`Corruption.zone`（M7 的世界事件設定）內的敵對生物 50% 機率帶 `rpgcore:corrupted` 效果：生命 +20%、攻擊附帶凋零 2 秒、紫色粒子（美術階段再換成貼圖層）。
- 效能（自測，100 隻亡靈對 100 隻盜匪）：**rpgcore 自己的 AI 0.7–1.5 ms/tick**（預算 2 ms）；包含原版 AI 的整體開銷 6–8 ms/tick。
## 13. 城市、任務、劇情【定 #11 #12 #13】
- `town/`：
  - `TownSavedData`：每座城市的狀態，連動 events
  - **重建：**服務 NPC 是 rpgcore 自己的實體 `rpgcore:npc`（不可破壞、站著不動、右鍵開對話），NPC id ＝ `<城市>/<角色>`，任務的 talk 目標用它。城市位置用 `/rpgcore town create <id>` 建立，模板在 `towns/*.json`。
  - 舊方案：用 Easy NPC 的實體（M0 確認）。對話選項用它的 `ConditionRegistry`／`ActionRegistry` 接任務旗標；顯示和隱藏沒有現成 API，由 `town/` 把 NPC 存成 NBT 移除、需要時放回。
  - 旅館床：設定重生點
- `quest/`：
  - `QuestDef` JSON：步驟、目標類型、對話節點、選擇、獎勵、導航目標
  - 目標類型：
    - `talk`
    - `reach`
    - `kill_specific`：只限指定的 Boss 或精英，不做殺 N 隻
    - `find_item`
    - `interact_block`
    - `event_outcome`
  - `QuestLogCapability`：每位玩家的任務狀態、線索、選擇紀錄
  - 日誌介面（按鍵開啟），導航指引可以在設定或介面裡關閉
- 主線：
  - 4 章，每章一個 `ChapterDef`，章節本身就是任務線，結尾的選擇寫進 `ProgressSavedData`
  - 終局條件：至少 3 章完成
  - 結局由各章選擇組合查表決定（`endings.json`）
- 對話：自製簡潔對話框（NPC 名稱、2–4 行文字、最多 3 個選項），文字全部放 lang 檔，方便日後修改和翻譯。

## 14. Boss 框架【定 #7】
- `boss/`：`BossDef` JSON 描述招式表：
  - 每個招式的動畫名稱、前搖秒數、判定範圍、傷害、冷卻、使用的階段
  - 階段轉換：血量門檻＋轉場動畫
  - 場地機制：只有機制型 Boss 才有，例如水晶、崩塌區、召喚、環境機關
- 實體以 GeckoLib 製作動畫，需要額外加入 GeckoLib 依賴。
- `BossArena`：Boss 房範圍、進場關門、Boss 血條、重置（全員死亡時 Boss 回滿、場地復原）、連動誓約石。
- 先用 1 個招式型測試 Boss 驗證框架，再做機制型。
- M5 實作（`boss/`）：
  - `BossDef`：`data/<ns>/rpgcore/bosses/*.json`，招式形狀 `circle`／`cone`／`charge`，每招有範圍、角度、前搖、傷害、冷卻、可用階段；`phases` 寫血量門檻和轉場秒數。
  - `RpgBoss`：狀態機 待機 → 前搖（地面粒子畫出判定範圍）→ 判定 → 收招；選招依表的順序、階段、冷卻、距離。轉場期間無敵。目前招式名稱／動畫名稱已經同步到用戶端，GeckoLib 等美術階段加入，現在用放大的殭屍模型＋屍殼貼圖佔位。
  - `BossArena`：祭壇方塊（方塊實體記錄 Boss ID、半徑 14）在玩家靠近時生成 Boss 並關上場地門；場內 100 tick 沒有玩家 → Boss 回滿、回到祭壇、開門；Boss 死亡 → 標記已擊敗、開門，不再生成。
  - 測試 Boss `rpgcore:test_boss`：生命 80（×5 = 400），第二階段（50%）解鎖衝鋒；招式：重砸（圓形 4 格）、橫掃（90° 扇形 5 格）、衝鋒（10 格）【提案】。

## 15. 環境機關【定 #9】
- 只用在地城的方塊和實體：
  - 炸藥桶：受傷後引爆，敵我都傷
  - 吊燈：鏈條被打斷後落下，造成傷害並暈眩
  - 反用陷阱：壓力板加箭矢或火焰發射器，對怪物有效
  - 可燃油地
- 地城房間規格裡標註機關位置；GameTest 驗證每個機關對怪物和玩家都有效。
- M5 實作（`dungeon/Mechanisms`）【數值都是提案】：
  - 炸藥桶：受任何傷害就爆，半徑 3，爆炸傷害算在打它的人身上，會連鎖引爆
  - 吊燈：掛在脆弱鏈條下；鏈條被打斷、失去支撐或被射中時落下，每落 1 格 3 傷害，落地 2 秒暈眩
  - 可燃油地：被燃燒的實體、燃燒的投射物或相鄰的火／岩漿點燃，連帶燒掉最多 256 格相連的油
  - 注意：原版只有實體移動時才檢查它站的方塊，靜止不動的怪物不會點燃油地。

## 16. 家園
- **重建：**藍圖是 rpgcore 自己的格式（`blueprints/*.json`：內嵌層資料或 `"structure"` 指向結構檔），不依賴 aibuildbridge。建築台：藍圖右鍵 → 預覽外框；蹲下右鍵旋轉；空手右鍵開始／暫停；材料從相鄰容器扣，每秒一層。文明解鎖用玩家資料＋文明卷軸，因為 1.20.1 的配方條件在載入時判斷、無法依玩家，所以改成限制藍圖與建築台。
- 舊方案：建築台方塊加藍圖物品（NBT 指向 aibuildbridge 藍圖 ID），投影預覽位置與方向；確認後從相鄰容器扣材料，伺服器每秒放置一層（#29）；移植前先做資料結構。
- 建材解鎖：玩家 Capability 記錄已解鎖文明，配方檢查用自訂配方條件。
- 獎盃：每個 Boss 一個可以放置的方塊（模型等美術階段再做）。

## 17. 移植（M6）
**重建：未進行。**這個 repo 沒有 aibuildbridge 與 VillagesAndRoads 的原始碼。城市與藍圖改用 rpgcore 內建的版本（§13、§16），日後要移植時再把原始碼放進來。
- aibuildbridge 先，VillagesAndRoads 後，因為 VillagesAndRoads 依賴前者。
- 主要差異：
  - 物品資料元件改回 NBT
  - `DeferredRegister` 的寫法
  - 事件匯流排（NeoForge 換回 Forge）
  - Codec 和註冊表 API 的細節
  - GameTest 註冊方式
- GameTest 全部移植，全綠才算完成。

## 18. 自動驗證
| 測試 | 內容 |
|---|---|
| AI GameTest | 每個角色的行為：射手是否保持距離、坦克是否擋射線、包抄手是否繞背、陣營之間是否互打 |
| 事件與任務 | 指令推進時間，檢查狀態轉換、佇列、獎勵分歧、任務步驟、結局查表 |
| 裂隙 | 生成、計時、層數倍率、超時處理、入口在天亮時移除（M8：`test/RiftGameTests`） |
| 地城與 Boss | 捷徑門、秘密牆、誓約石重生、陷阱、炸藥桶、吊燈、油地、Boss 招式／階段／重置／場地門（M5：`test/DungeonGameTests`） |
| 開服自測 | 載入整個模組包 → 預生成 → 輸出錯誤、警告、tick 耗時報告（沿用 VillagesAndRoads 的 ServerSelfTest 做法） |
| 特性 GameTest | 每條特性至少一個測試：觸發、條件、效果數值、跨流派過濾、遞迴保護（M2：`test/TraitGameTests`） |
| 模組包內 GameTest | 開服自測在真正的模組包裡註冊並執行全部 GameTest（`test/PackGameTests`；正式環境 Forge 不跑 GameTest，所以由自測自己推進）。開發環境 `runGameTestServer` 只有原版，需要 TACZ／Iron's 的測試在那裡標為略過 |
| 掉落 GameTest | 模擬 10,000 次擊殺，確認機率分布、保底 |
| 危險度 | 輸出危險度地圖 PNG |
| 地城 | 生成後輸出俯視和側視 PNG，檢查捷徑門和誓約石 |
| 手感 | 戰鬥和 Boss 的手感才請玩家實機測試 |

## 19. 里程碑對應（所有設計問題都已結案，沒有阻擋項）
**重建狀態：**M0–M9 的程式框架與示範資料都在這個 repo（M6 的移植除外，見 §17）。M10 內容填充未開始。下表的 ✅ 是原 `C:\mc` 的狀態。
| M | 內容 | 前置 |
|---|---|---|
| M0 ✅ | JDK17、MDK、packwiz 模組包、開服自測、**API 驗證報告**（§5.2、§7、§13 Easy NPC、GeckoLib） | — |
| M1 ✅ | stat、combat（Dash、尺度 ×5、生命條）、槍與魔法獨立的屬性；Apex 風格槍包的資料骨架（佔位模型） | M0 |
| M2 ✅ | trait 引擎＋10 件原型傳奇 | M1 |
| M3 ✅ | loot、forge、死亡規則 | M2 |
| M4 ✅ | danger＋Champions＋ai＋ecology | M3 |
| M5 ✅ | dungeon＋環境機關＋boss 框架＋古堡樣板＋測試 Boss | M4 |
| M6 | 移植＋town＋quest 框架 | M5 |
| M7 | events＋主線章節框架＋示範事件 | M6 |
| M8 ✅ | rift | M4 |
| M9 | home | M6 |
| M10 | 內容填充：區域、地城、Boss、任務、傳奇（依美術進度） | 全部 |

## 20. 重建版的驗證狀態
已在雲端環境用 Forge 47.4.10（MDG legacyforge 2.0.147、Gradle 9.2.1、JDK 17）實際建置與執行：

| 項目 | 方式 | 結果 |
|---|---|---|
| 編譯 | `gradlew clean build` | 成功，0 個警告（已改用 `ResourceLocation.fromNamespaceAndPath/parse` 與建構子注入的 `FMLJavaModLoadingContext`） |
| 純邏輯 | JUnit（`logic` 子專案與主專案的 `test`） | 22/22 通過 |
| GameTest | `gradlew runGameTestServer`（原版＋rpgcore） | 20 個必要測試全過＋1 個選用測試通過；TACZ／Iron's 的 2 個測試在沒裝時標為略過 |
| 全生物 ×5 | GameTest `allEntitiesAreScaledX5`（`SelfTest.entityProblems`） | 原版與 rpgcore 所有生物生成後都 ×5 且滿血 |
| 古堡 | GameTest `castleGenerates`（像 `/place structure`）與 `naturalCastleGenerates`（找最近的自然古堡並生成整片區塊） | 都是 7 塊房間、0 個殘留 Jigsaw；誓約石 1、Boss 祭壇 1、秘密牆 42、捷徑門 2、場地門 9、吊燈 5、炸藥桶 8、箱子 4。俯視／側視圖輸出到 `run/rpgcore_selftest/` |
| 資料載入 | 伺服器 log | 39 特性、113 個 rpgcore 資料檔、無錯誤；缺 TACZ／Iron's 時跳過 4 件傳奇、5 個基底（預期） |

注意：
- GameTest 的相對座標 y=0 是結構方塊那一列，`empty` 樣板的地板在 y=1，測試從 y=2 開始放東西。
- 用 `/place` 或 `castleGenerates` 放進已生成的地形時，房間會被山丘蓋住一部分；自然生成有 `beard_thin` 地形調整，不會這樣。

**還沒驗證的：**
1. 用戶端（HUD、說明框、鍛造台／對話／日誌介面、渲染器）：這個環境沒有顯示器，無法開客戶端。
2. 反射綁定的外部模組名稱（`compat/`）：要裝 TACZ／Iron's／Curios 才能測。不符時伺服器 log 會出現 `rpgcore compat: missing ...`。
3. 傳奇與基底裡的外部物品 id（`tacz:modern_kinetic_gun` 的 GunId、`irons_spellbooks:*`）。
4. 手感：戰鬥、Dash、Boss 招式需要玩家實機測試。

## 21. 資料檔一覽（`data/<ns>/rpgcore/`）
| 資料夾 | 內容 |
|---|---|
| traits/ | 特性（minor 23、unique 11、inscription 5） |
| uniques/ | 10 件原型傳奇＋1 件神器 |
| inscriptions/ | 5 種核心 → 銘刻特性 |
| bases/ | 裝備基底（近戰、弓、護甲、槍、法術書、飾品） |
| loot_sources/ | mob、elite、boss、chest、rift、event_held、event_reclaimed、quest |
| danger/config.json | 環距離、維度、生態域、結構 |
| factions/、squads/、spawns/ | 陣營、小隊、生怪表 |
| bosses/ | test_boss（重砸、橫掃、二階段衝鋒） |
| rift/config.json | 裂隙全部數值 |
| events/ | riverside_siege（示範事件：傳聞 → 圍城 → 守住／陷落 → 收復） |
| towns/、dialogues/、quests/、chapters/、endings.json | 河畔鎮、三個 NPC 對話、遺失的戒指、第一章 |
| blueprints/ | cottage、watchtower |

## 22. 指令（權限 2）
`/rpgcore unique <id> [tier]`、`loot <source> [tier]`、`gear <rarity> [tier]`、`danger`、`elite`、`aitime`、`boss <id>`、`rift open [tier]`、`rift list`、`event start|advance|goal|force|status`、`progress set|clear|list`、`ending`、`town create <id>`、`town inn <id>`、`npc <role> <dialogue>`、`quest start|advance|reset`、`blueprint <id>`、`unlock <civ>`、`scroll <civ>`、`selftest`、`selftest castle`
