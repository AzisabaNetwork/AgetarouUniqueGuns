# AgetarouUniqueGuns プラグイン全機能まとめ

## 概要
このプラグインはCrackShotおよびCrackShotPlusを拡張し、高度な武器システムを提供します。
個別の武器クラスと、汎用的な武器システムクラスで構成されています。

---

## 1. 汎用武器システム

### UniversalWeaponSystem
CrackShot武器に高度な機能を追加する汎用システム。

#### 1.1 即時リロード機能 (Instant_Reload)
```yaml
Instant_Reload:
  Enable: true
  Add_Amount: 1              # 追加弾数
  Cooldown_Ticks: 20         # クールダウン
  Reload_On_Sneak: true      # シフト中リロード
  Sound: "SOUND-VOLUME-PITCH"
  Message: "&aリロード完了"
  Delay_Bar:
    Enable: true
    Text: "&7リロード中... &f{progress}%"
    Color: "§7"
    Style: "SOLID"
```

#### 1.2 爆発制御機能 (On_Hit_Explosion)
```yaml
On_Hit_Explosion:
  Enable: true
  Explosion_Chance_Entity: 0.3  # エンティティ命中時爆発確率
  Explosion_Chance_Block: 0.2    # ブロック命中時爆発確率
  Explosion_Power: 3.0
  Explosion_Fire: false
  Explosion_Block_Break: true
  Sound: "ENTITY_GENERIC_EXPLODE-1.0-1.0"
  Message: "&a爆発！"
```

#### 1.3 潜り爆発ショット (Sneak_Explosive_Shot)
```yaml
Sneak_Explosive_Shot:
  Enable: true
  Extra_Ammo_Cost: 1          # 追加弾薬消費
  Explosion_Power: 4.0
  Explosion_Fire: true
  Sound: "ENTITY_GENERIC_EXPLODE-1.0-1.5"
  Message: "&e爆発ショット！"
```

#### 1.4 持ち替えロック機能 (Switch_Lock)
```yaml
Switch_Lock:
  Enable: true
  Lock_Duration_Ticks: 60      # ロック時間
  Shoot_Block_Message: "&c射撃できません！"
  Sound: "ENTITY_ITEM_BREAK-1.0-1.0"
  Delay_Bar:
    Enable: true
    Text: "&7持ち替えロック中... &f{progress}%"
```

#### 1.5 モデルデータ変更機能
```yaml
Custom_Model_Data_Held: 1001     # 持っている時のモデル
Custom_Model_Data_Default: 1000   # 通常時のモデル
```

---

### WeaponsSPMode
高度な武器変更とキルストリークシステムを提供。

#### 2.1 基本武器変更機能 (WhenChangeWeapon)
```yaml
WhenChangeWeapon:
  Enable: true
  Shift: "武器ID"              # シフトで武器変更
  Empty_Ammo: "武器ID"          # 弾丸切れで武器変更
  Off_And_Shift: "武器ID"       # シフト＋オフハンド持ち替え
  Jump_And_Shift: "武器ID"       # シフト＋ジャンプ
  Jump_And_Off: "武器ID"         # ジャンプ中＋オフハンド持ち替え
  Kill_Streak:
    "数字": "武器ID"            # 指定キル数で武器変更
  Timed_Change:
    Delay_Ticks: 200            # 武器入手後何tickで変更
    Target_Weapon: "武器ID"       # 変更先武器
    Sound: "SOUND-VOLUME-PITCH"
    Message: "&a武器変更！"
    Delay_Bar:
      Enable: true
      Action_Bar: "&7武器変更中... {bar}"
      End_Action_Bar: "&a武器変更完了！"
      End_Sound: "ENTITY_PLAYER_LEVELUP-1.0-1.0"
      Symbol: "|"
      Symbol_Amount: 15
      Left_Color: "&a"
      Right_Color: "&c"
  Sound: "SOUND-VOLUME-PITCH"    # 武器変更時の音
  CoolDown: 20                  # クールダウン（tick）
  If_HaveItems:
    イベント名:
      Item_type: "MATERIAL"
      Item_name: "表示名"
      Values: 1                  # 必要個数
  Message: "&a武器を変更しました"
```

#### 2.2 武器固有キルストリークシステム (KillStreak)
```yaml
KillStreak:
  Enable: true
  Kill_Count: 5                 # 最大キル数
  Streak_Icon:
    Enable: true                 # カウンター表示
    Right: "◀"                  # 未貯蔵分シンボル
    Left: "▶"                   # 貯まった分シンボル
  Ignore_Limit: false           # 上限超えを許可
  Remove_Streak: true            # 死亡時にストリーク削除
  Remove_Several_Streak: -1     # 死亡時削除数（-1で全削除）
  Remove_Server_Move: true       # サーバー移動時に全削除
  Accumulate_Streaks_Defeat_Mobs: false  # モブキルもカウント
```

#### 2.3 ストリークイベントシステム (Streak_Event)
```yaml
Streak_Event:
  Enable: true
  Cost_Count:                   # 指定数消費イベント
    Cost_Count: 3
    Change_Weapons: "武器ID"
    Change_Weapons_WithAction: "shift,jump,offhand"
    Takeover_Streak: true       # ストリーク引継ぎ
    Cmd: "say #shooter#が使用"
    Actionbar: "&aメッセージ"
    Saychat: "&eチャット"
    Sound: "SOUND-VOLUME-PITCH"
  Cost_MaxCount:                # 満タン時全消費イベント
    Notenough_Actionbar: "&c不足"
    Notenough_Saychat: "&eストリーク不足！"
    # Cost_Countと同様の設定
```

#### 2.4 死亡時元の武器復帰機能
- **WhenChangeWeapon用**: 死亡時に元の武器に自動復帰
- **KillStreak用**: ストリークイベントで変更した武器も復帰
- **メッセージ**: 各機能で異なる復帰メッセージを表示

---

## 2. 個別武器クラス

### 2.1 HurtfulSpine
- **機能**: キルストリークに基づいてプレイヤーに移動速度ボーナス
- **特徴**: 
  - キル数に応じて段階的に速度向上
  - 死亡時にリセット
  - 武器スキン変更機能

### 2.2 Sorcerers_Rod
- **機能**: 魔法弾を発射する杖
- **特徴**:
  - マナシステム（自動回復）
  - 4つの元素属性（BLANK, FIRE, WATER, EARTH）
  - 属性ごとに異なる効果
  - リロードでマナ回復

### 2.3 AntiOnePunchMan
- **機能**: ワンパンチ対策武器
- **特徴**:
  - 特定条件下でダメージ軽減
  - 反撃効果

### 2.4 Kar98kWhiteSharkシリーズ
- **機能**: キル時の弾薬回復
- **特徴**:
  - 一定確率でキル時に弾薬追加
  - 複数のバリエーション

### 2.5 CarolOfTheOldOnes
- **機能**: 特殊効果付き武器
- **特徴**:
  - 時間経過での効果変化
  - 状態異常付与

### 2.6 その他武器
- **EPT_Jager**: 特殊な狙撃銃
- **Grimoire**: 魔法書武器
- **TensinoDantouDai**: 特殊な近接武器
- **testweaponシリーズ**: テスト用武器

---

## 3. 基本システム

### 3.1 WeaponBase
- **機能**: 全武器の基底クラス
- **提供**:
  - プレイヤーステータス更新の抽象化
  - 持ち替えイベントの統一処理
  - 死亡イベントの統一処理

### 3.2 WeaponConfig
- **機能**: 武器設定ファイルの管理
- **提供**:
  - YAMLファイルの読み込み
  - 設定値の取得とキャッシュ
  - 設定変更の動的反映

### 3.3 CSUtilities
- **機能**: CrackShot関連のユーティリティ
- **提供**:
  - 武器タイトル取得
  - 武器名の正規化
  - APIの簡易化

---

## 4. 共通機能

### 4.1 音形式統一
- すべての音設定で `SOUND-VOLUME-PITCH-DELAY` 形式
- 複数音のカンマ区切り、遅延再生に対応

### 4.2 プログレスバー表示
- 統一されたプログレスバー形式
- カスタマイズ可能なシンボルと色
- 進行状況のリアルタイム表示

### 4.3 タスク管理
- 武器持ち替えで自動的に各種タスクをキャンセル
- プレイヤー退出時に全タスクをクリーンアップ
- メモリリーク防止

### 4.4 イベント優先度制御
- 適切なイベント処理順序
- 他プラグインとの競合防止

---

## 5. 設定例

### 5.1 高度な武器の例
```yaml
# UniversalWeaponSystem機能
Instant_Reload:
  Enable: true
  Add_Amount: 2

On_Hit_Explosion:
  Enable: true
  Explosion_Chance_Entity: 0.1

Sneak_Explosive_Shot:
  Enable: true
  Extra_Ammo_Cost: 2

# WeaponsSPMode機能
WhenChangeWeapon:
  Enable: true
  Shift: "Shotgun"
  Timed_Change:
    Delay_Ticks: 100
    Target_Weapon: "Rifle"
    Delay_Bar:
      Enable: true
      Action_Bar: "&7変更中... {bar}"

KillStreak:
  Enable: true
  Kill_Count: 3
  Streak_Icon:
    Enable: true
  Streak_Event:
    Enable: true
    Cost_Count:
      Cost_Count: 2
      Change_Weapons: "SpecialWeapon"
      Takeover_Streak: true
```

---

## 6. 使用方法

### 6.1 基本設定
1. 武器YAMLファイルを作成
2. 必要な機能セクションを追加
3. プラグインをリロード

### 6.2 機能の組み合わせ
- 複数の機能を同時に設定可能
- 条件分岐で複雑な動作を実現
- プレイヤー体験のカスタマイズ

### 6.3 バランス調整
- 確率値で出現頻度を調整
- クールダウンで連続使用を制限
- コストでリスクとリターンを設定

---

## 7. 技術仕様

### 7.1 依存関係
- **必須**: CrackShot
- **推奨**: CrackShotPlus
- **対応バージョン**: Minecraft 1.16+

### 7.2 パフォーマンス
- 非同期処理の積極的使用
- 効率的なタスク管理
- メモリ使用量の最適化

### 7.3 互換性
- 他の武器プラグインとの共存
- 既存設定の尊重
- APIの標準化

---

## 8. 開発情報

### 8.1 構造
```
src/main/java/com/github/aburaagetarou/agetarouuniqueguns/
├── AgetarouUniqueGuns.java          # メインクラス
├── WeaponConfig.java               # 設定管理
├── utils/
│   └── CSUtilities.java           # ユーティリティ
├── listeners/
│   └── CSListeners.java           # リスナー群
└── weapons/
    ├── WeaponBase.java           # 基底クラス
    ├── UniversalWeaponSystem.java # 汎用システム
    ├── WeaponsSPMode.java       # SPモードシステム
    └── [個別武器クラス群]     # 個別武器
```

### 8.2 拡張性
- 新武器クラスの追加が容易
- 機能モジュールの独立
- APIの拡張性

---

このプラグインにより、Minecraftサーバーで高度な武器システムを実現し、プレイヤーに多彩な戦闘体験を提供します。
