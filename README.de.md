# NaiGen — Android Nai2API Text-zu-Bild-Client

![Release](https://img.shields.io/github/v/release/ook826092-cloud/naigen-app?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/ook826092-cloud/naigen-app/build.yml?style=flat-square&label=build)
![License](https://img.shields.io/github/license/ook826092-cloud/naigen-app?style=flat-square)
![Stars](https://img.shields.io/github/stars/ook826092-cloud/naigen-app?style=flat-square)

**Sprachen:** [简体中文](README.md) ｜ [English](README.en.md) ｜ [日本語](README.ja.md) ｜ [한국어](README.ko.md) ｜ [Français](README.fr.md) ｜ Deutsch ｜ [Español](README.es.md)

Ein nativer Android Kotlin-Client basierend auf dem Nai2API Text-zu-Bild-Tutorial (v7.12). **Das Backend ist vollständig mit Android-nativen Technologien implementiert** (OkHttp + Coroutines + Room + DataStore), ohne Python- oder externe Server-Abhängigkeiten.

## Download

Neueste Version: [Releases-Seite](https://github.com/ook826092-cloud/naigen-app/releases/latest)

Der Workflow wird **nur bei manueller Auslösung** gestartet (Actions → Run workflow). Er wird:
1. Die PATCH-Version automatisch erhöhen (v2.0.0 → v2.0.1 → ...) und ins Repo committen
2. Ein signiertes Release-APK kompilieren (mit einem Keystore)
3. Automatisch ein GitHub Release mit APK + SHA256-Prüfsumme erstellen

## Tutorial-Funktionszuordnung

| Tutorial-Abschnitt | Tutorial-Implementierung | Diese App |
|---|---|---|
| §5.1 Nai2API-Überblick | `https://nai.sta1n.cn` Job-Async-Modus | `NaiApiClient` + `NaiRepository.generate()` |
| §5.5.8 `_get_artist_string` Style-Routing | Python-Methode | `StyleRegistry.resolveArtistString()` |
| §5.5.10 Job-Async-Fluss | `requests.post` + `time.sleep` Polling | `client.createJob` + `delay()` + `client.pollJob` |
| §5.5.11 Text-zu-Bild | Python synchrone Methode | `NaiRepository.generate()` suspend-Funktion |
| §5.5.13 29 Community-Styles | `nai_styles.json`-Datei | `CommunityStyles.ALL` Kotlin-Konstanten |
| §5.7.3 7 eingebaute Styles | `ARTIST_PRESETS`-Dict | `ArtistPresets.ALL` (mit 7.12 Artist-Strings) |
| §5.7.4 `--variants N` gleichzeitig | `ThreadPoolExecutor` | `generateVariants()` über `async{}.awaitAll()` |
| §5.5.4 Auto-Style-Erkennung | `AUTO_STYLE_KEYWORDS`-Tabelle | `AutoStyleKeywords.detect()` |
| §5.5.12 Guthabenabfrage | `GET /api/me` | `NaiRepository.checkBalance()` |
| §5.12 Dateibenennung `YYYYMMDD_NNN.png` | `next_dated_output_path` | `ImageSaver.savePrivate()` |
| §5.6 7 eingebaute Presets | 2.5d/fresh/doujin/galgame/comicDoujin/animeOld/realistic_loli | gleich |
| §5.8 9 Größenoptionen | Hoch-/Querformat/Quadrat × Standard/2K/4K | `SizeOptions.ALL` |
| §5.10 alle CLI-Argumente | `--steps/--scale/--cfg/--sampler/--negative/--artist` | „Erweiterte Parameter"-Panel |

## Tech-Stack

| Schicht | Wahl | Grund |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Moderne deklarative UI |
| Visueller Stil | iOS-Minimalismus (Weiß/Grau + dünne Rahmen + iOS-Blau) | Sauber, fokussiert |
| Asynchron | Kotlin Coroutines + Flow | Ersetzt Pythons `time.sleep` + `ThreadPoolExecutor` |
| Netzwerk | OkHttp 4.12 + kotlinx.serialization | Ersetzt Python `requests` |
| DB | Room 2.6 (HistoryDao + FavoritesDao) | Verlauf + Prompt-Favoriten |
| Einstellungen | Preferences DataStore | Ersetzt Python `.env` |
| Bildladen | Coil 2.7 | ByteArray-Laden + Galerie-Speichern |
| Hintergrund | Vordergrunddienst (`dataSync`-Typ) | Überlebt App-Hintergrund |
| Widget | AppWidgetProvider + RemoteViews | Schnell-Generierungs-Widget |
| Min SDK | Android 8.0 (API 26) | 95%+ Geräteabdeckung |
| Target SDK | Android 15 (API 35) | Android 15/16 Vordergrunddienst-Konformität |

## Bereitstellung

### 1. Klonen & in Android Studio öffnen

```bash
git clone https://github.com/ook826092-cloud/naigen-app.git
```

In Android Studio öffnen → auf Gradle-Sync warten (~500 MB Abhängigkeiten beim ersten Mal).

### 2. Nai2API-Token setzen

Nach APK-Installation: App öffnen → Settings-Tab → `STA1N-xxxxx…`-Token ausfüllen.

Token wird nur im privaten DataStore der App gespeichert — nirgendwo hochgeladen.

### 3. Release-APK bauen

```bash
./gradlew assembleRelease
# Ausgabe: app/build/outputs/apk/release/app-release.apk (mit einem Keystore signiert)
```

### 4. Installation

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Verwendung

### Basis-Generierung

1. App öffnen, englischen Prompt im Generate-Tab eingeben
2. Style wählen (Standard: 2.5d) — 36 Presets im Styles-Tab
3. Größe wählen (Standard: Hochformat)
4. „Generate" antippen — ~30-40s warten
5. Ergebnisbild wird angezeigt; „Save to gallery" oder „Share" antippen

### Gleichzeitige Varianten

- Variantenanzahl (1-6) im „Style/Size"-Panel anpassen
- N unabhängige Jobs gleichzeitig einreichen, Zeit ≈ Einzelgenerierung
- Beste auswählen

### Auto-Style-Erkennung

- „Auto style detection" im Smart-Panel umschalten
- Style wechselt automatisch basierend auf Prompt-Schlüsselwörtern (z. B. „watercolor" → community:4)

### Erweiterte Parameter

- „Custom artist string"-Zeile ausklappen
- Überschreibbar: Steps / Scale / CFG / Sampler / benutzerdefinierter Artist-String

### Guthabenabfrage

- ⚡-Button unten, oder Settings → Check balance

### Verlauf

- History-Tab: Miniatur + Prompt + Style + Dauer + Zeit
- Pro Eintrag: In Galerie speichern / Teilen / Löschen

### Prompt-Favoriten

- Favorites-Tab → „+" zum Hinzufügen
- Wiederverwendbare Prompt-Templates speichern, bei Bedarf als negativ markieren

## Schutz gegen Style-Routing-Bug

Das Tutorial §5.5.17 warnt vor dem „Alle Ausgaben sehen aus wie 2.5d"-Bug. Diese App verhindert das durch:

1. **`StyleRegistry.resolveArtistString()`** implementiert vollständiges 3-Stufen-Routing:
   - ① Community-Style `community:ID` → verwendet `communityStyles.byId(id).artistString`
   - ② Eingebautes Preset → verwendet `artistPresets.get(key).artistString`
   - ③ Keines von beiden → gibt Eingabe unverändert zurück (als benutzerdefinierter Artist-String behandelt)

2. **`NaiRepository.generate()`** ruft immer `resolveArtistString(styleKey)` auf, um vor dem API-Versand zu übersetzen

3. **`customArtist`-Feld** (entspricht `--artist`) überschreibt `styleKey` wenn nicht-leer und trifft Route ③

## Datenschutz & Berechtigungen

| Berechtigung | Zweck | Wann |
|---|---|---|
| INTERNET | Nai2API aufrufen | Immer |
| FOREGROUND_SERVICE_DATA_SYNC | Hintergrund-Polling | Beim Start von GenerationService |
| POST_NOTIFICATIONS | Fortschrittsbenachrichtigungen | Android 13+ beim ersten Start |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Batterie-Whitelist | Von der Keep-Alive-Seite |
| READ_MEDIA_IMAGES | Galerie-Speichern | Nur Android 13+ |
| WRITE_EXTERNAL_STORAGE | Galerie-Speichern | Android 9 und niedriger |

**Alle Benutzerdaten (Token / Verlauf / Favoriten / Bilder) bleiben im privaten Speicher der App. Nichts wird hochgeladen.**

## Hintergrund-Keep-Alive

### Herstellererkennung

Die App erkennt den Gerätehersteller über `Build.MANUFACTURER` + `Build.BRAND` + Systemeigenschafts-Reflexion. Abdeckung:

- Xiaomi / Redmi (MIUI)
- Huawei (EMUI / HarmonyOS)
- Honor (MagicOS)
- OPPO (ColorOS)
- OnePlus (in ColorOS integriert)
- vivo / iQOO (OriginOS / Funtouch OS)
- Samsung (One UI)
- Meizu (Flyme)
- Realme
| Native Android

### Guide-Fluss

Der Keep-Alive-Tab wird:
1. Erkannten Hersteller + maßgeschneiderte Tipps anzeigen
2. Benötigte Keep-Alive-Elemente auflisten (Autostart / Batterie / Hintergrund-Popup / Benachrichtigungen)
3. Jedes Element hat eine Sprung-Schaltfläche — öffnet direkt die Einstellungen-Unterseite des Herstellers
4. Aktuellen Status anzeigen (Batterie-Whitelist / Benachrichtigungsberechtigung) — bei Fortsetzung automatisch aktualisiert
5. Fallback: App-Details-Seite (funktioniert auf allen ROMs)

### Android 15 / 16-Anpassung

- `targetSdk = 35`
- `foregroundServiceType = dataSync` im Manifest deklariert
- `startForeground` mit Type-Überladung (API 34+)
- `FOREGROUND_SERVICE_IMMEDIATE`-Verhalten + sofortiger `startForeground`-Aufruf (Android 16s 6-Sekunden-Regel)
- Getrennte Benachrichtigungskanäle: Fortschritt (LOW) + Ergebnis (DEFAULT)

## Leistungsoptimierungen

| Dimension | Optimierung | Ort |
|---|---|---|
| Netzwerk | Geteiltes OkHttp-Singleton, Connection-Pool + HTTP/2 | `NaiApiClient.client` |
| Bildladen | Globaler Coil ImageLoader, 25% Memory-Cache, 50MB Disk | `NaiApplication.newImageLoader()` |
| Bildladen | inSampleSize-Downsampling für Miniaturen, verhindert OOM | `ImageSaver.makeThumbnail()` |
| Compose | `@Immutable` auf StylePreset / GenRequest / GenResult etc. | `data/model/Models.kt` |
| Datenbank | Indizes auf createdAt / styleKey / tag / isNegative | Entity-Klassen |
| Coroutines | `ensureActive()`-Prüfung in Polling-Schleife | `NaiRepository.generate()` |
| Zustand | StateFlow + combine, `collectAsStateWithLifecycle` | `GenerateViewModel` |
| Benachrichtigungen | `setOnlyAlertOnce(true)` + `setSilent(true)` | `buildProgressNotification()` |

## Fehlerbehebung

### Gradle-Sync-Timeout (China-Netzwerk)

In `settings.gradle.kts` `mavenCentral()` durch Aliyun-Spiegel ersetzen:

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### Generierung fehlgeschlagen: „API Token not configured"

Settings öffnen, `STA1N-...`-Token ausfüllen. Token ist standardmäßig maskiert.

### Generierung fehlgeschlagen: „HTTP 401"

Token falsch oder abgelaufen. Neuen von Ihrem Nai2API-Anbieter holen.

### Polling-Timeout (180s)

Server-Warteschlange zu lang. Suchen Sie `MAX_POLL_TIME_MS` in `NaiRepository.kt` und ändern Sie `180_000L` zu `300_000L`.

### KSP Room-Schema-Fehler

Bereinigen und neu bauen: `./gradlew clean` → `./gradlew assembleRelease`.

## Version

- v2.0.x (2026-07-15) — aktuell. Siehe [Releases](https://github.com/ook826092-cloud/naigen-app/releases) für vollständiges Changelog
- v1.0.0 (2026-07-15) — initial

## Lizenz

MIT — siehe [LICENSE](LICENSE). Nai2API-Dienst und Kauf-Links sind Drittanbieter; folgen Sie deren Nutzungsbedingungen.
