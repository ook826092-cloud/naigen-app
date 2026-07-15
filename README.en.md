# NaiGen — Android API Text-to-Image Client

![Release](https://img.shields.io/github/v/release/ook826092-cloud/naigen-app?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/ook826092-cloud/naigen-app/build.yml?style=flat-square&label=build)
![License](https://img.shields.io/github/license/ook826092-cloud/naigen-app?style=flat-square)
![Stars](https://img.shields.io/github/stars/ook826092-cloud/naigen-app?style=flat-square)

**Languages:** [简体中文](README.md) ｜ English ｜ [日本語](README.ja.md) ｜ [한국어](README.ko.md) ｜ [Français](README.fr.md) ｜ [Deutsch](README.de.md) ｜ [Español](README.es.md)

A native Android Kotlin client based on the API text-to-image tutorial (v7.12). **Backend is fully implemented with Android-native technologies** (OkHttp + Coroutines + Room + DataStore), with no Python or external server dependencies.

## Download

Latest version: [Releases page](https://github.com/ook826092-cloud/naigen-app/releases/latest)

The workflow runs **only when manually triggered** (via Actions → Run workflow). It will:
1. Auto-increment the PATCH version (v2.0.0 → v2.0.1 → ...) and commit back to the repo
2. Compile a signed Release APK (using a keystore)
3. Auto-create a GitHub Release with the APK + SHA256 checksum

## Tutorial Feature Mapping

| Tutorial Section | Tutorial Implementation | This App |
|---|---|---|
| §5.1 API overview | `https://API 服务器` Job async mode | `NaiApiClient` + `NaiRepository.generate()` |
| §5.5.8 `_get_artist_string` style routing | Python method | `StyleRegistry.resolveArtistString()` |
| §5.5.10 Job async flow | `requests.post` + `time.sleep` poll | `client.createJob` + `delay()` + `client.pollJob` |
| §5.5.11 text-to-image | Python sync method | `NaiRepository.generate()` suspend fn |
| §5.5.13 29 community styles | `nai_styles.json` file | `CommunityStyles.ALL` Kotlin constants |
| §5.7.3 7 built-in styles | `ARTIST_PRESETS` dict | `ArtistPresets.ALL` (with 7.12 artist strings) |
| §5.7.4 `--variants N` concurrent | `ThreadPoolExecutor` | `generateVariants()` via `async{}.awaitAll()` |
| §5.5.4 auto-style detection | `AUTO_STYLE_KEYWORDS` table | `AutoStyleKeywords.detect()` |
| §5.5.12 balance query | `GET /api/me` | `NaiRepository.checkBalance()` |
| §5.12 file naming `YYYYMMDD_NNN.png` | `next_dated_output_path` | `ImageSaver.savePrivate()` |
| §5.6 7 built-in presets | 2.5d/fresh/doujin/galgame/comicDoujin/animeOld/realistic_loli | same |
| §5.8 9 size options | portrait/landscape/square × standard/2K/4K | `SizeOptions.ALL` |
| §5.10 all CLI args | `--steps/--scale/--cfg/--sampler/--negative/--artist` | "Advanced params" panel |

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Modern declarative UI |
| Visual style | iOS minimalist (white/gray + thin borders + iOS blue) | Clean, focused |
| Async | Kotlin Coroutines + Flow | Replaces Python's `time.sleep` + `ThreadPoolExecutor` |
| Network | OkHttp 4.12 + kotlinx.serialization | Replaces Python `requests` |
| DB | Room 2.6 (HistoryDao + FavoritesDao) | History + Prompt favorites |
| Prefs | Preferences DataStore | Replaces Python `.env` |
| Image loading | Coil 2.7 | ByteArray loading + gallery save |
| Background | Foreground service (`dataSync` type) | Survives app backgrounding |
| Widget | AppWidgetProvider + RemoteViews | Quick-gen widget |
| Min SDK | Android 8.0 (API 26) | 95%+ device coverage |
| Target SDK | Android 15 (API 35) | Android 15/16 foreground service compliance |

## Deploy

### 1. Clone & open in Android Studio

```bash
git clone https://github.com/ook826092-cloud/naigen-app.git
```

Open in Android Studio → wait for Gradle sync (downloads ~500MB dependencies on first run).

### 2. Set API Token

After installing the APK: open App → Settings tab → fill in your `STA1N-xxxxx…` token.

Token is stored only in the app's private DataStore — never uploaded anywhere.

### 3. Build Release APK

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk (signed with a keystore)
```

### 4. Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Usage

### Basic generation

1. Open the app, enter an English prompt on the Generate tab
2. Pick a style (default: 2.5d) — 36 presets available on the Styles tab
3. Pick a size (default: portrait)
4. Tap "Generate" — wait ~30-40s
5. Result shows the image; tap "Save to gallery" or "Share"

### Concurrent variants

- Adjust variant count (1-6) in the "Style/Size" panel
- Submit N independent jobs simultaneously, time ≈ single gen
- Pick the best one

### Auto style detection

- Toggle "Auto style detection" in the Smart panel
- Style auto-switches based on prompt keywords (e.g. "watercolor" → community:4)

### Advanced params

- Expand "Custom artist string" row
- Override: Steps / Scale / CFG / Sampler / custom artist string

### Balance check

- Tap the ⚡ button at the bottom, or Settings → Check balance

### History

- History tab: thumbnail + prompt + style + duration + time
- Per item: Save to gallery / Share / Delete

### Prompt favorites

- Favorites tab → "+" to add
- Save reusable prompt templates, mark as negative if needed

## Style Routing Bug Guard

The tutorial §5.5.17 warns about the "all outputs look like 2.5d" bug. This app prevents it via:

1. **`StyleRegistry.resolveArtistString()`** implements full 3-tier routing:
   - ① Community style `community:ID` → uses `communityStyles.byId(id).artistString`
   - ② Built-in preset → uses `artistPresets.get(key).artistString`
   - ③ Neither → returns input as-is (treated as custom artist string)

2. **`NaiRepository.generate()`** always calls `resolveArtistString(styleKey)` to translate before sending to the API

3. **`customArtist` field** (corresponds to `--artist`) overrides `styleKey` when non-empty, hitting route ③

## Privacy & Permissions

| Permission | Purpose | When |
|---|---|---|
| INTERNET | Call API | Always |
| FOREGROUND_SERVICE_DATA_SYNC | Background polling | When GenerationService starts |
| POST_NOTIFICATIONS | Progress notifications | Android 13+ on first launch |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Battery whitelist | From keep-alive page |
| READ_MEDIA_IMAGES | Gallery save | Android 13+ only |
| WRITE_EXTERNAL_STORAGE | Gallery save | Android 9 and below |

**All user data (token / history / favorites / images) stays in the app's private storage. Nothing is uploaded.**

## Background Keep-Alive

### Manufacturer detection

The app detects the device manufacturer via `Build.MANUFACTURER` + `Build.BRAND` + system property reflection. Coverage:

- Xiaomi / Redmi (MIUI)
- Huawei (EMUI / HarmonyOS)
- Honor (MagicOS)
- OPPO (ColorOS)
- OnePlus (merged into ColorOS)
- vivo / iQOO (OriginOS / Funtouch OS)
- Samsung (One UI)
- Meizu (Flyme)
- Realme
- Native Android

### Guide flow

The Keep-Alive tab will:
1. Show detected manufacturer + tailored tips
2. List the keep-alive items needed (autostart / battery / background popup / notifications)
3. Each item has a jump button — directly opens the manufacturer's settings subpage
4. Show current state (battery whitelist / notification permission) — auto-refreshes on resume
5. Fallback: app details page (works on all ROMs)

### Android 15 / 16 adaptation

- `targetSdk = 35`
- `foregroundServiceType = dataSync` declared in Manifest
- `startForeground` with type overload (API 34+)
- `FOREGROUND_SERVICE_IMMEDIATE` behavior + immediate `startForeground` call (Android 16's 6-second rule)
- Separate notification channels: progress (LOW) + result (DEFAULT)

## Performance Optimizations

| Dimension | Optimization | Location |
|---|---|---|
| Network | Shared OkHttp singleton, connection pool + HTTP/2 | `NaiApiClient.client` |
| Image loading | Global Coil ImageLoader, 25% memory cache, 50MB disk | `NaiApplication.newImageLoader()` |
| Image loading | inSampleSize downsampling for thumbnails, prevents OOM | `ImageSaver.makeThumbnail()` |
| Compose | `@Immutable` on StylePreset / GenRequest / GenResult etc. | `data/model/Models.kt` |
| Database | Indexes on createdAt / styleKey / tag / isNegative | Entity classes |
| Coroutines | `ensureActive()` check in poll loop | `NaiRepository.generate()` |
| State | StateFlow + combine, `collectAsStateWithLifecycle` | `GenerateViewModel` |
| Notifications | `setOnlyAlertOnce(true)` + `setSilent(true)` | `buildProgressNotification()` |

## Troubleshooting

### Gradle sync timeout (China network)

In `settings.gradle.kts`, replace `mavenCentral()` with Aliyun mirrors:

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### Generation fails: "API Token not configured"

Open Settings, fill in `STA1N-...` token. Token is masked by default.

### Generation fails: "HTTP 401"

Token wrong or expired. Get a new one from your API provider.

### Poll timeout (180s)

Server queue too long. Search `MAX_POLL_TIME_MS` in `NaiRepository.kt` and change `180_000L` to `300_000L`.

### KSP Room schema error

Clean and rebuild: `./gradlew clean` → `./gradlew assembleRelease`.

## Version

- v2.0.x (2026-07-15) — current. See [Releases](https://github.com/ook826092-cloud/naigen-app/releases) for full changelog
- v1.0.0 (2026-07-15) — initial

## License

MIT — see [LICENSE](LICENSE). API service and purchase links are third-party; follow their terms of service.
