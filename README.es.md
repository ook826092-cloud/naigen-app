# NaiGen — Cliente Android API de generación de texto a imagen

![Release](https://img.shields.io/github/v/release/ook826092-cloud/naigen-app?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/ook826092-cloud/naigen-app/build.yml?style=flat-square&label=build)
![License](https://img.shields.io/github/license/ook826092-cloud/naigen-app?style=flat-square)
![Stars](https://img.shields.io/github/stars/ook826092-cloud/naigen-app?style=flat-square)

**Idiomas:** [简体中文](README.md) ｜ [English](README.en.md) ｜ [日本語](README.ja.md) ｜ [한국어](README.ko.md) ｜ [Français](README.fr.md) ｜ [Deutsch](README.de.md) ｜ Español

Un cliente Android nativo en Kotlin basado en el tutorial API de generación de texto a imagen (v7.12). **El backend está completamente implementado con tecnologías nativas de Android** (OkHttp + Coroutines + Room + DataStore), sin dependencias de Python ni servidores externos.

## Descarga

Última versión: [Página de Releases](https://github.com/ook826092-cloud/naigen-app/releases/latest)

El workflow se ejecuta **solo al activarse manualmente** (Actions → Run workflow). Realizará:
1. Auto-incrementar la versión PATCH (v2.0.0 → v2.0.1 → ...) y commitear al repo
2. Compilar un APK Release firmado (con un keystore)
3. Crear automáticamente un GitHub Release con el APK + checksum SHA256

## Mapeo de funciones del tutorial

| Sección del tutorial | Implementación del tutorial | Esta App |
|---|---|---|
| §5.1 Visión general API | Modo async Job `https://API 服务器` | `NaiApiClient` + `NaiRepository.generate()` |
| §5.5.8 Enrutamiento de estilo `_get_artist_string` | Método Python | `StyleRegistry.resolveArtistString()` |
| §5.5.10 Flujo async Job | `requests.post` + `time.sleep` polling | `client.createJob` + `delay()` + `client.pollJob` |
| §5.5.11 texto a imagen | Método sincrónico Python | `NaiRepository.generate()` función suspend |
| §5.5.13 29 estilos comunitarios | Archivo `nai_styles.json` | Constantes Kotlin `CommunityStyles.ALL` |
| §5.7.3 7 estilos integrados | dict `ARTIST_PRESETS` | `ArtistPresets.ALL` (con cadenas de artista 7.12) |
| §5.7.4 `--variants N` concurrente | `ThreadPoolExecutor` | `generateVariants()` vía `async{}.awaitAll()` |
| §5.5.4 detección automática de estilo | Tabla `AUTO_STYLE_KEYWORDS` | `AutoStyleKeywords.detect()` |
| §5.5.12 consulta de saldo | `GET /api/me` | `NaiRepository.checkBalance()` |
| §5.12 nombrado de archivo `YYYYMMDD_NNN.png` | `next_dated_output_path` | `ImageSaver.savePrivate()` |
| §5.6 7 presets integrados | 2.5d/fresh/doujin/galgame/comicDoujin/animeOld/realistic_loli | igual |
| §5.8 9 opciones de tamaño | retrato/paisaje/cuadrado × estándar/2K/4K | `SizeOptions.ALL` |
| §5.10 todos los args CLI | `--steps/--scale/--cfg/--sampler/--negative/--artist` | Panel «Parámetros avanzados» |

## Stack técnico

| Capa | Elección | Razón |
|---|---|---|
| UI | Jetpack Compose + Material 3 | UI declarativa moderna |
| Estilo visual | Minimalista iOS (blanco/gris + bordes finos + azul iOS) | Limpio, enfocado |
| Asíncrono | Kotlin Coroutines + Flow | Reemplaza `time.sleep` + `ThreadPoolExecutor` de Python |
| Red | OkHttp 4.12 + kotlinx.serialization | Reemplaza `requests` de Python |
| BD | Room 2.6 (HistoryDao + FavoritesDao) | Historial + Favoritos de prompts |
| Preferencias | Preferences DataStore | Reemplaza `.env` de Python |
| Carga de imágenes | Coil 2.7 | Carga de ByteArray + guardado en galería |
| Segundo plano | Servicio en primer plano (tipo `dataSync`) | Sobrevive al backgrounding de la app |
| Widget | AppWidgetProvider + RemoteViews | Widget de generación rápida |
| SDK mínimo | Android 8.0 (API 26) | Cobertura del 95%+ de dispositivos |
| SDK objetivo | Android 15 (API 35) | Cumplimiento de servicio en primer plano Android 15/16 |

## Despliegue

### 1. Clonar y abrir en Android Studio

```bash
git clone https://github.com/ook826092-cloud/naigen-app.git
```

Abrir en Android Studio → esperar a que se complete la sincronización de Gradle (descarga ~500 MB de dependencias la primera vez).

### 2. Configurar el token API

Tras instalar el APK: abrir la app → pestaña Settings → rellenar el token `STA1N-xxxxx…`.

El token se almacena solo en el DataStore privado de la app — nunca se sube a ningún lado.

### 3. Compilar APK Release

```bash
./gradlew assembleRelease
# Salida: app/build/outputs/apk/release/app-release.apk (firmado con un keystore)
```

### 4. Instalación

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Uso

### Generación básica

1. Abrir la app, ingresar un prompt en inglés en la pestaña Generate
2. Elegir un estilo (por defecto: 2.5d) — 36 presets en la pestaña Styles
3. Elegir un tamaño (por defecto: retrato)
4. Tocar «Generate» — esperar ~30-40s
5. Se muestra la imagen resultante; tocar «Save to gallery» o «Share»

### Variantes concurrentes

- Ajustar el conteo de variantes (1-6) en el panel «Style/Size»
- Enviar N jobs independientes simultáneamente, tiempo ≈ generación única
- Elegir la mejor

### Detección automática de estilo

- Activar «Auto style detection» en el panel Smart
- El estilo cambia automáticamente según palabras clave del prompt (ej. «watercolor» → community:4)

### Parámetros avanzados

- Expandir la fila «Custom artist string»
| Sobrescribible: Steps / Scale / CFG / Sampler / cadena de artista personalizada

### Consulta de saldo

- Botón ⚡ abajo, o Settings → Check balance

### Historial

- Pestaña History: miniatura + prompt + estilo + duración + hora
- Por ítem: Guardar en galería / Compartir / Eliminar

### Favoritos de prompts

- Pestaña Favorites → «+» para añadir
- Guardar plantillas de prompts reutilizables, marcar como negativo si es necesario

## Protección contra el bug de enrutamiento de estilo

El tutorial §5.5.17 advierte sobre el bug «todas las salidas parecen 2.5d». Esta app lo previene mediante:

1. **`StyleRegistry.resolveArtistString()`** implementa enrutamiento completo de 3 niveles:
   - ① Estilo comunitario `community:ID` → usa `communityStyles.byId(id).artistString`
   - ② Preset integrado → usa `artistPresets.get(key).artistString`
   - ③ Ninguno → devuelve la entrada tal cual (tratada como cadena de artista personalizada)

2. **`NaiRepository.generate()`** siempre llama a `resolveArtistString(styleKey)` para traducir antes de enviar a la API

3. **Campo `customArtist`** (corresponde a `--artist`) sobrescribe `styleKey` si no está vacío, llegando a la ruta ③

## Privacidad y permisos

| Permiso | Propósito | Cuándo |
|---|---|---|
| INTERNET | Llamar a API | Siempre |
| FOREGROUND_SERVICE_DATA_SYNC | Polling en segundo plano | Al iniciar GenerationService |
| POST_NOTIFICATIONS | Notificaciones de progreso | Android 13+ en el primer lanzamiento |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Lista blanca de batería | Desde la página keep-alive |
| READ_MEDIA_IMAGES | Guardar en galería | Solo Android 13+ |
| WRITE_EXTERNAL_STORAGE | Guardar en galería | Android 9 e inferior |

**Todos los datos del usuario (token / historial / favoritos / imágenes) permanecen en el almacenamiento privado de la app. Nada se sube.**

## Mantener vivo en segundo plano

### Detección del fabricante

La app detecta el fabricante del dispositivo vía `Build.MANUFACTURER` + `Build.BRAND` + reflexión de propiedad del sistema. Cobertura:

- Xiaomi / Redmi (MIUI)
- Huawei (EMUI / HarmonyOS)
- Honor (MagicOS)
- OPPO (ColorOS)
- OnePlus (fusionado en ColorOS)
- vivo / iQOO (OriginOS / Funtouch OS)
- Samsung (One UI)
- Meizu (Flyme)
- Realme
- Android nativo

### Flujo de guía

La pestaña Keep-Alive:
1. Muestra el fabricante detectado + consejos adaptados
2. Lista los elementos keep-alive necesarios (autostart / batería / popup en segundo plano / notificaciones)
3. Cada elemento tiene un botón de salto — abre directamente la subpágina de ajustes del fabricante
4. Muestra el estado actual (lista blanca de batería / permiso de notificación) — se actualiza automáticamente al reanudar
5. Fallback: página de detalles de la app (funciona en todas las ROMs)

### Adaptación Android 15 / 16

- `targetSdk = 35`
- `foregroundServiceType = dataSync` declarado en el Manifest
- `startForeground` con sobrecarga de tipo (API 34+)
- Comportamiento `FOREGROUND_SERVICE_IMMEDIATE` + llamada inmediata a `startForeground` (regla de 6 segundos de Android 16)
- Canales de notificación separados: progreso (LOW) + resultado (DEFAULT)

## Optimizaciones de rendimiento

| Dimensión | Optimización | Ubicación |
|---|---|---|
| Red | Singleton OkHttp compartido, pool de conexiones + HTTP/2 | `NaiApiClient.client` |
| Carga de imágenes | ImageLoader Coil global, caché de memoria 25%, disco 50 MB | `NaiApplication.newImageLoader()` |
| Carga de imágenes | Downsampling inSampleSize para miniaturas, previene OOM | `ImageSaver.makeThumbnail()` |
| Compose | `@Immutable` en StylePreset / GenRequest / GenResult etc. | `data/model/Models.kt` |
| Base de datos | Índices en createdAt / styleKey / tag / isNegative | Clases Entity |
| Coroutines | Comprobación `ensureActive()` en bucle de polling | `NaiRepository.generate()` |
| Estado | StateFlow + combine, `collectAsStateWithLifecycle` | `GenerateViewModel` |
| Notificaciones | `setOnlyAlertOnce(true)` + `setSilent(true)` | `buildProgressNotification()` |

## Solución de problemas

### Timeout de sincronización de Gradle (red de China)

En `settings.gradle.kts`, reemplazar `mavenCentral()` con espejos de Aliyun:

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### Generación fallida: «API Token not configured»

Abrir Settings, rellenar el token `STA1N-...`. El token está enmascarado por defecto.

### Generación fallida: «HTTP 401»

Token incorrecto o expirado. Obtén uno nuevo de tu proveedor de API.

### Timeout de polling (180s)

La cola del servidor es demasiado larga. Busca `MAX_POLL_TIME_MS` en `NaiRepository.kt` y cambia `180_000L` a `300_000L`.

### Error de esquema Room de KSP

Limpiar y reconstruir: `./gradlew clean` → `./gradlew assembleRelease`.

## Versión

- v2.0.x (2026-07-15) — actual. Ver [Releases](https://github.com/ook826092-cloud/naigen-app/releases) para el changelog completo
- v1.0.0 (2026-07-15) — inicial

## Licencia

MIT — ver [LICENSE](LICENSE). El servicio API y los enlaces de compra son de terceros; sigue sus términos de servicio.
