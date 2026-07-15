# NaiGen — Android Nai2API テキストから画像生成クライアント

![Release](https://img.shields.io/github/v/release/ook826092-cloud/naigen-app?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/ook826092-cloud/naigen-app/build.yml?style=flat-square&label=build)
![License](https://img.shields.io/github/license/ook826092-cloud/naigen-app?style=flat-square)
![Stars](https://img.shields.io/github/stars/ook826092-cloud/naigen-app?style=flat-square)

**言語:** [简体中文](README.md) ｜ [English](README.en.md) ｜ 日本語 ｜ [한국어](README.ko.md) ｜ [Français](README.fr.md) ｜ [Deutsch](README.de.md) ｜ [Español](README.es.md)

Nai2API テキストから画像生成チュートリアル (v7.12) に基づくネイティブ Android Kotlin クライアント。**バックエンドは完全に Android ネイティブ技術** (OkHttp + Coroutines + Room + DataStore) で実装され、Python や外部サーバーに依存しません。

## ダウンロード

最新版: [Releases ページ](https://github.com/ook826092-cloud/naigen-app/releases/latest)

ワークフローは**手動トリガー時のみ**実行されます (Actions → Run workflow)。実行内容:
1. PATCH バージョンを自動インクリメント (v2.0.0 → v2.0.1 → ...) しリポジトリにコミット
2. 署名付き Release APK をコンパイル (keystore を使用)
3. GitHub Release を自動作成し、APK と SHA256 チェックサムを添付

## チュートリアル機能マッピング

| チュートリアル節 | チュートリアル実装 | 本 App |
|---|---|---|
| §5.1 Nai2API 概要 | `https://nai.sta1n.cn` Job 非同期モード | `NaiApiClient` + `NaiRepository.generate()` |
| §5.5.8 `_get_artist_string` スタイルルーティング | Python メソッド | `StyleRegistry.resolveArtistString()` |
| §5.5.10 Job 非同期フロー | `requests.post` + `time.sleep` ポーリング | `client.createJob` + `delay()` + `client.pollJob` |
| §5.5.11 テキストから画像 | Python 同期メソッド | `NaiRepository.generate()` suspend 関数 |
| §5.5.13 29 のコミュニティスタイル | `nai_styles.json` ファイル | `CommunityStyles.ALL` Kotlin 定数 |
| §5.7.3 7 つの組み込みスタイル | `ARTIST_PRESETS` dict | `ArtistPresets.ALL` (7.12 アーティスト文字列付き) |
| §5.7.4 `--variants N` 同時生成 | `ThreadPoolExecutor` | `generateVariants()` は `async{}.awaitAll()` を使用 |
| §5.5.4 自動スタイル検出 | `AUTO_STYLE_KEYWORDS` テーブル | `AutoStyleKeywords.detect()` |
| §5.5.12 残高照会 | `GET /api/me` | `NaiRepository.checkBalance()` |
| §5.12 ファイル命名 `YYYYMMDD_NNN.png` | `next_dated_output_path` | `ImageSaver.savePrivate()` |
| §5.6 7 つの組み込みプリセット | 2.5d/fresh/doujin/galgame/comicDoujin/animeOld/realistic_loli | 同上 |
| §5.8 9 つのサイズオプション | 縦/横/正方形 × 標準/2K/4K | `SizeOptions.ALL` |
| §5.10 全 CLI 引数 | `--steps/--scale/--cfg/--sampler/--negative/--artist` | 「詳細パラメータ」パネル |

## 技術スタック

| レイヤー | 選択 | 理由 |
|---|---|---|
| UI | Jetpack Compose + Material 3 | モダンな宣言型 UI |
| ビジュアルスタイル | iOS ミニマリスト (白/グレー + 細い境界線 + iOS ブルー) | クリーン、集中 |
| 非同期 | Kotlin Coroutines + Flow | Python の `time.sleep` + `ThreadPoolExecutor` を代替 |
| ネットワーク | OkHttp 4.12 + kotlinx.serialization | Python `requests` を代替 |
| DB | Room 2.6 (HistoryDao + FavoritesDao) | 履歴 + プロンプトお気に入り |
| 設定 | Preferences DataStore | Python `.env` を代替 |
| 画像読み込み | Coil 2.7 | ByteArray 読み込み + ギャラリー保存 |
| バックグラウンド | フォアグラウンドサービス (`dataSync` タイプ) | アプリバックグラウンドでも存続 |
| ウィジェット | AppWidgetProvider + RemoteViews | クイック生成ウィジェット |
| 最小 SDK | Android 8.0 (API 26) | 95% 以上のデバイスをカバー |
| ターゲット SDK | Android 15 (API 35) | Android 15/16 フォアグラウンドサービス準拠 |

## デプロイ

### 1. クローン & Android Studio で開く

```bash
git clone https://github.com/ook826092-cloud/naigen-app.git
```

Android Studio で開き、Gradle 同期が完了するまで待ちます (初回は約 500MB の依存関係をダウンロード)。

### 2. Nai2API トークンを設定

APK インストール後: App を開く → Settings タブ → `STA1N-xxxxx…` トークンを入力。

トークンはアプリのプライベート DataStore にのみ保存され、どこにもアップロードされません。

### 3. Release APK をビルド

```bash
./gradlew assembleRelease
# 出力: app/build/outputs/apk/release/app-release.apk (keystore で署名)
```

### 4. インストール

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 使用方法

### 基本生成

1. アプリを開き、Generate タブで英語プロンプトを入力
2. スタイルを選択 (デフォルト: 2.5d) — Styles タブに 36 のプリセット
3. サイズを選択 (デフォルト: 縦)
4. 「生成」をタップ — 約 30〜40 秒待機
5. 結果画像が表示、「ギャラリーに保存」または「共有」をタップ

### 同時バリアント

- 「スタイル/サイズ」パネルでバリアント数 (1-6) を調整
- N 個の独立した Job を同時に送信、所要時間 ≈ 単一生成
- 最良のものを選択

### 自動スタイル検出

- Smart パネルで「自動スタイル検出」をトグル
- プロンプトキーワードに基づいてスタイルを自動切替 (例: 「watercolor」→ community:4)

### 詳細パラメータ

- 「カスタムアーティスト文字列」行を展開
- 上書き可能: Steps / Scale / CFG / Sampler / カスタムアーティスト文字列

### 残高照会

- 下部の ⚡ ボタン、または Settings → 残高確認

### 履歴

- History タブ: サムネイル + プロンプト + スタイル + 所要時間 + 時刻
- 各項目: ギャラリーに保存 / 共有 / 削除

### プロンプトお気に入り

- Favorites タブ → 「+」で追加
- 再利用可能なプロンプトテンプレートを保存、必要に応じてネガティブとしてマーク

## スタイルルーティングバグ対策

チュートリアル §5.5.17 は「全出力が 2.5d に見える」バグについて警告しています。本 App は以下で防止:

1. **`StyleRegistry.resolveArtistString()`** が完全な 3 段階ルーティングを実装:
   - ① コミュニティスタイル `community:ID` → `communityStyles.byId(id).artistString` を使用
   - ② 組み込みプリセット → `artistPresets.get(key).artistString` を使用
   - ③ どちらでもない → 入力をそのまま返す (カスタムアーティスト文字列として扱う)

2. **`NaiRepository.generate()`** は常に `resolveArtistString(styleKey)` を呼び出して API 送信前に翻訳

3. **`customArtist` フィールド** (`--artist` に対応) は非空時に `styleKey` を上書きし、ルート ③ にヒット

## プライバシーと権限

| 権限 | 用途 | タイミング |
|---|---|---|
| INTERNET | Nai2API 呼び出し | 常時 |
| FOREGROUND_SERVICE_DATA_SYNC | バックグラウンドポーリング | GenerationService 起動時 |
| POST_NOTIFICATIONS | 進捗通知 | Android 13+ 初回起動時 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | バッテリーホワイトリスト | keep-alive ページから |
| READ_MEDIA_IMAGES | ギャラリー保存 | Android 13+ のみ |
| WRITE_EXTERNAL_STORAGE | ギャラリー保存 | Android 9 以下 |

**全ユーザーデータ (トークン / 履歴 / お気に入り / 画像) はアプリのプライベートストレージに留まります。何もアップロードされません。**

## バックグラウンド keep-alive

### メーカー検出

アプリは `Build.MANUFACTURER` + `Build.BRAND` + システムプロパティリフレクションでデバイスメーカーを検出します。対応:

- Xiaomi / Redmi (MIUI)
- Huawei (EMUI / HarmonyOS)
- Honor (MagicOS)
- OPPO (ColorOS)
- OnePlus (ColorOS に統合)
- vivo / iQOO (OriginOS / Funtouch OS)
- Samsung (One UI)
- Meizu (Flyme)
- Realme
- ネイティブ Android

### ガイドフロー

Keep-Alive タブは:
1. 検出されたメーカー + 個別のヒントを表示
2. 必要な keep-alive 項目 (自動起動 / バッテリー / バックグラウンドポップアップ / 通知) をリスト
3. 各項目にジャンプボタン — メーカーの設定サブページを直接開く
4. 現在の状態 (バッテリーホワイトリスト / 通知権限) を表示 — レジューム時に自動リフレッシュ
5. フォールバック: アプリ詳細ページ (全 ROM で動作)

### Android 15 / 16 対応

- `targetSdk = 35`
- Manifest で `foregroundServiceType = dataSync` を宣言
- `startForeground` を type 指定オーバーロードで呼び出し (API 34+)
- `FOREGROUND_SERVICE_IMMEDIATE` ビヘイビア + 即時 `startForeground` 呼び出し (Android 16 の 6 秒ルール)
- 通知チャンネル分離: 進捗 (LOW) + 結果 (DEFAULT)

## パフォーマンス最適化

| 次元 | 最適化 | 場所 |
|---|---|---|
| ネットワーク | 共有 OkHttp シングルトン、コネクションプール + HTTP/2 | `NaiApiClient.client` |
| 画像読み込み | グローバル Coil ImageLoader、メモリキャッシュ 25%、ディスク 50MB | `NaiApplication.newImageLoader()` |
| 画像読み込み | サムネイルに inSampleSize ダウンサンプリング、OOM を防止 | `ImageSaver.makeThumbnail()` |
| Compose | StylePreset / GenRequest / GenResult 等に `@Immutable` | `data/model/Models.kt` |
| データベース | createdAt / styleKey / tag / isNegative にインデックス | Entity クラス |
| コルーチン | ポーリングループで `ensureActive()` チェック | `NaiRepository.generate()` |
| 状態 | StateFlow + combine、`collectAsStateWithLifecycle` | `GenerateViewModel` |
| 通知 | `setOnlyAlertOnce(true)` + `setSilent(true)` | `buildProgressNotification()` |

## トラブルシューティング

### Gradle 同期タイムアウト (中国ネットワーク)

`settings.gradle.kts` で `mavenCentral()` を Aliyun ミラーに置換:

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### 生成失敗: 「API Token not configured」

Settings を開き、`STA1N-...` トークンを入力。トークンはデフォルトでマスク表示されます。

### 生成失敗: 「HTTP 401」

トークンが間違っているか期限切れ。Nai2API プロバイダーから新しいものを取得してください。

### ポーリングタイムアウト (180秒)

サーバーキューが長すぎます。`NaiRepository.kt` で `MAX_POLL_TIME_MS` を検索し、`180_000L` を `300_000L` に変更してください。

### KSP Room スキーマエラー

クリーンして再ビルド: `./gradlew clean` → `./gradlew assembleRelease`。

## バージョン

- v2.0.x (2026-07-15) — 現行。完全な変更履歴は [Releases](https://github.com/ook826092-cloud/naigen-app/releases) を参照
- v1.0.0 (2026-07-15) — 初版

## ライセンス

MIT — [LICENSE](LICENSE) を参照。Nai2API サービスおよび購入リンクはサードパーティです; それぞれの利用規約に従ってください。
