# NaiGen — Android Nai2API 텍스트-이미지 생성 클라이언트

![Release](https://img.shields.io/github/v/release/ook826092-cloud/naigen-app?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/ook826092-cloud/naigen-app/build.yml?style=flat-square&label=build)
![License](https://img.shields.io/github/license/ook826092-cloud/naigen-app?style=flat-square)
![Stars](https://img.shields.io/github/stars/ook826092-cloud/naigen-app?style=flat-square)

**언어:** [简体中文](README.md) ｜ [English](README.en.md) ｜ [日本語](README.ja.md) ｜ 한국어 ｜ [Français](README.fr.md) ｜ [Deutsch](README.de.md) ｜ [Español](README.es.md)

Nai2API 텍스트-이미지 생성 튜토리얼 (v7.12) 기반 네이티브 Android Kotlin 클라이언트. **백엔드는 완전히 Android 네이티브 기술** (OkHttp + Coroutines + Room + DataStore)로 구현되었으며, Python이나 외부 서버에 의존하지 않습니다.

## 다운로드

최신 버전: [Releases 페이지](https://github.com/ook826092-cloud/naigen-app/releases/latest)

워크플로우는 **수동 트리거 시에만** 실행됩니다 (Actions → Run workflow). 실행 내용:
1. PATCH 버전 자동 증가 (v2.0.0 → v2.0.1 → ...) 후 리포지토리에 커밋
2. 서명된 Release APK 컴파일 (keystore 사용)
3. GitHub Release 자동 생성, APK + SHA256 체크섬 첨부

## 튜토리얼 기능 매핑

| 튜토리얼 섹션 | 튜토리얼 구현 | 본 App |
|---|---|---|
| §5.1 Nai2API 개요 | `https://nai.sta1n.cn` Job 비동기 모드 | `NaiApiClient` + `NaiRepository.generate()` |
| §5.5.8 `_get_artist_string` 스타일 라우팅 | Python 메서드 | `StyleRegistry.resolveArtistString()` |
| §5.5.10 Job 비동기 흐름 | `requests.post` + `time.sleep` 폴링 | `client.createJob` + `delay()` + `client.pollJob` |
| §5.5.11 텍스트-이미지 | Python 동기 메서드 | `NaiRepository.generate()` suspend 함수 |
| §5.5.13 29개 커뮤니티 스타일 | `nai_styles.json` 파일 | `CommunityStyles.ALL` Kotlin 상수 |
| §5.7.3 7개 내장 스타일 | `ARTIST_PRESETS` dict | `ArtistPresets.ALL` (7.12 아티스트 문자열 포함) |
| §5.7.4 `--variants N` 동시 생성 | `ThreadPoolExecutor` | `generateVariants()`는 `async{}.awaitAll()` 사용 |
| §5.5.4 자동 스타일 감지 | `AUTO_STYLE_KEYWORDS` 테이블 | `AutoStyleKeywords.detect()` |
| §5.5.12 잔액 조회 | `GET /api/me` | `NaiRepository.checkBalance()` |
| §5.12 파일 명명 `YYYYMMDD_NNN.png` | `next_dated_output_path` | `ImageSaver.savePrivate()` |
| §5.6 7개 내장 프리셋 | 2.5d/fresh/doujin/galgame/comicDoujin/animeOld/realistic_loli | 동일 |
| §5.8 9개 크기 옵션 | 세로/가로/정사각 × 표준/2K/4K | `SizeOptions.ALL` |
| §5.10 모든 CLI 인자 | `--steps/--scale/--cfg/--sampler/--negative/--artist` | "고급 매개변수" 패널 |

## 기술 스택

| 레이어 | 선택 | 이유 |
|---|---|---|
| UI | Jetpack Compose + Material 3 | 모던 선언형 UI |
| 비주얼 스타일 | iOS 미니멀리스트 (흰색/회색 + 얇은 테두리 + iOS 블루) | 깔끔하고 집중 |
| 비동기 | Kotlin Coroutines + Flow | Python의 `time.sleep` + `ThreadPoolExecutor` 대체 |
| 네트워크 | OkHttp 4.12 + kotlinx.serialization | Python `requests` 대체 |
| DB | Room 2.6 (HistoryDao + FavoritesDao) | 히스토리 + 프롬프트 즐겨찾기 |
| 설정 | Preferences DataStore | Python `.env` 대체 |
| 이미지 로딩 | Coil 2.7 | ByteArray 로딩 + 갤러리 저장 |
| 백그라운드 | 포그라운드 서비스 (`dataSync` 타입) | 앱 백그라운드 전환 후에도 유지 |
| 위젯 | AppWidgetProvider + RemoteViews | 빠른 생성 위젯 |
| 최소 SDK | Android 8.0 (API 26) | 95% 이상 기기 커버 |
| 타겟 SDK | Android 15 (API 35) | Android 15/16 포그라운드 서비스 준수 |

## 배포

### 1. 클론 & Android Studio에서 열기

```bash
git clone https://github.com/ook826092-cloud/naigen-app.git
```

Android Studio에서 열고 Gradle 동기화가 완료될 때까지 대기 (첫 실행 시 약 500MB 의존성 다운로드).

### 2. Nai2API 토큰 설정

APK 설치 후: App 열기 → Settings 탭 → `STA1N-xxxxx…` 토큰 입력.

토큰은 앱의 private DataStore에만 저장되며, 어디에도 업로드되지 않습니다.

### 3. Release APK 빌드

```bash
./gradlew assembleRelease
# 출력: app/build/outputs/apk/release/app-release.apk (keystore로 서명)
```

### 4. 설치

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 사용법

### 기본 생성

1. App 열기, Generate 탭에서 영어 프롬프트 입력
2. 스타일 선택 (기본: 2.5d) — Styles 탭에 36개 프리셋
3. 크기 선택 (기본: 세로)
4. "생성" 탭 — 약 30~40초 대기
5. 결과 이미지 표시, "갤러리에 저장" 또는 "공유" 탭

### 동시 변형

- "스타일/크기" 패널에서 변형 수 (1-6) 조정
- N개의 독립적인 Job을 동시에 제출, 소요 시간 ≈ 단일 생성
- 최상의 것 선택

### 자동 스타일 감지

- Smart 패널에서 "자동 스타일 감지" 토글
- 프롬프트 키워드 기반 스타일 자동 전환 (예: "watercolor" → community:4)

### 고급 매개변수

- "사용자 정의 아티스트 문자열" 행 확장
- 재정의 가능: Steps / Scale / CFG / Sampler / 사용자 정의 아티스트 문자열

### 잔액 조회

- 하단 ⚡ 버튼, 또는 Settings → 잔액 확인

### 히스토리

- History 탭: 썸네일 + 프롬프트 + 스타일 + 소요 시간 + 시각
- 각 항목: 갤러리에 저장 / 공유 / 삭제

### 프롬프트 즐겨찾기

- Favorites 탭 → "+"로 추가
- 재사용 가능한 프롬프트 템플릿 저장, 필요시 네거티브로 표시

## 스타일 라우팅 버그 방지

튜토리얼 §5.5.17은 "모든 출력이 2.5d처럼 보임" 버그에 대해 경고합니다. 본 App은 다음으로 방지:

1. **`StyleRegistry.resolveArtistString()`** 이 완전한 3단계 라우팅을 구현:
   - ① 커뮤니티 스타일 `community:ID` → `communityStyles.byId(id).artistString` 사용
   - ② 내장 프리셋 → `artistPresets.get(key).artistString` 사용
   - ③ 둘 다 아님 → 입력을 그대로 반환 (사용자 정의 아티스트 문자열로 취급)

2. **`NaiRepository.generate()`** 는 항상 `resolveArtistString(styleKey)`를 호출하여 API 전송 전에 번역

3. **`customArtist` 필드** (`--artist`에 해당)는 비어있지 않을 때 `styleKey`를 재정의하여 라우트 ③에 히트

## 프라이버시 및 권한

| 권한 | 용도 | 시기 |
|---|---|---|
| INTERNET | Nai2API 호출 | 항상 |
| FOREGROUND_SERVICE_DATA_SYNC | 백그라운드 폴링 | GenerationService 시작 시 |
| POST_NOTIFICATIONS | 진행 알림 | Android 13+ 첫 실행 시 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 배터리 화이트리스트 | keep-alive 페이지에서 |
| READ_MEDIA_IMAGES | 갤러리 저장 | Android 13+만 |
| WRITE_EXTERNAL_STORAGE | 갤러리 저장 | Android 9 이하 |

**모든 사용자 데이터 (토큰 / 히스토리 / 즐겨찾기 / 이미지)는 앱의 private 저장소에 남습니다. 아무것도 업로드되지 않습니다.**

## 백그라운드 keep-alive

### 제조사 감지

앱은 `Build.MANUFACTURER` + `Build.BRAND` + 시스템 속성 리플렉션으로 기기 제조사를 감지합니다. 지원:

- Xiaomi / Redmi (MIUI)
- Huawei (EMUI / HarmonyOS)
- Honor (MagicOS)
- OPPO (ColorOS)
- OnePlus (ColorOS에 병합)
- vivo / iQOO (OriginOS / Funtouch OS)
- Samsung (One UI)
- Meizu (Flyme)
- Realme
- 네이티브 Android

### 가이드 흐름

Keep-Alive 탭은:
1. 감지된 제조사 + 맞춤형 팁 표시
2. 필요한 keep-alive 항목 (자동 시작 / 배터리 / 백그라운드 팝업 / 알림) 나열
3. 각 항목에 점프 버튼 — 제조사의 설정 하위 페이지를 직접 열기
4. 현재 상태 (배터리 화이트리스트 / 알림 권한) 표시 — resume 시 자동 새로고침
5. 폴백: 앱 상세 페이지 (모든 ROM에서 작동)

### Android 15 / 16 대응

- `targetSdk = 35`
- Manifest에 `foregroundServiceType = dataSync` 선언
- `startForeground`를 type 오버로드로 호출 (API 34+)
- `FOREGROUND_SERVICE_IMMEDIATE` 동작 + 즉시 `startForeground` 호출 (Android 16의 6초 규칙)
- 알림 채널 분리: 진행 (LOW) + 결과 (DEFAULT)

## 성능 최적화

| 차원 | 최적화 | 위치 |
|---|---|---|
| 네트워크 | 공유 OkHttp 싱글톤, 커넥션 풀 + HTTP/2 | `NaiApiClient.client` |
| 이미지 로딩 | 글로벌 Coil ImageLoader, 메모리 캐시 25%, 디스크 50MB | `NaiApplication.newImageLoader()` |
| 이미지 로딩 | 썸네일에 inSampleSize 다운샘플링, OOM 방지 | `ImageSaver.makeThumbnail()` |
| Compose | StylePreset / GenRequest / GenResult 등에 `@Immutable` | `data/model/Models.kt` |
| 데이터베이스 | createdAt / styleKey / tag / isNegative에 인덱스 | Entity 클래스 |
| 코루틴 | 폴링 루프에서 `ensureActive()` 체크 | `NaiRepository.generate()` |
| 상태 | StateFlow + combine, `collectAsStateWithLifecycle` | `GenerateViewModel` |
| 알림 | `setOnlyAlertOnce(true)` + `setSilent(true)` | `buildProgressNotification()` |

## 문제 해결

### Gradle 동기화 타임아웃 (중국 네트워크)

`settings.gradle.kts`에서 `mavenCentral()`을 Aliyun 미러로 교체:

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### 생성 실패: "API Token not configured"

Settings를 열고 `STA1N-...` 토큰 입력. 토큰은 기본적으로 마스크 표시됩니다.

### 생성 실패: "HTTP 401"

토큰이 잘못되었거나 만료됨. Nai2API 제공자로부터 새 것을 받으세요.

### 폴링 타임아웃 (180초)

서버 큐가 너무 깁니다. `NaiRepository.kt`에서 `MAX_POLL_TIME_MS`를 검색하여 `180_000L`을 `300_000L`로 변경하세요.

### KSP Room 스키마 오류

클린 후 재빌드: `./gradlew clean` → `./gradlew assembleRelease`.

## 버전

- v2.0.x (2026-07-15) — 현재. 전체 변경 이력은 [Releases](https://github.com/ook826092-cloud/naigen-app/releases) 참조
- v1.0.0 (2026-07-15) — 초기 버전

## 라이선스

MIT — [LICENSE](LICENSE) 참조. Nai2API 서비스 및 구매 링크는 서드파티입니다; 각각의 이용약관을 따르세요.
