# NaiGen — Client Android Nai2API de génération texte-vers-image

![Release](https://img.shields.io/github/v/release/ook826092-cloud/naigen-app?style=flat-square)
![Build](https://img.shields.io/github/actions/workflow/status/ook826092-cloud/naigen-app/build.yml?style=flat-square&label=build)
![License](https://img.shields.io/github/license/ook826092-cloud/naigen-app?style=flat-square)
![Stars](https://img.shields.io/github/stars/ook826092-cloud/naigen-app?style=flat-square)

**Langues :** [简体中文](README.md) ｜ [English](README.en.md) ｜ [日本語](README.ja.md) ｜ [한국어](README.ko.md) ｜ Français ｜ [Deutsch](README.de.md) ｜ [Español](README.es.md)

Un client Android natif Kotlin basé sur le tutoriel Nai2API de génération texte-vers-image (v7.12). **Le backend est entièrement implémenté avec des technologies Android natives** (OkHttp + Coroutines + Room + DataStore), sans dépendance Python ni serveur externe.

## Téléchargement

Dernière version : [Page des Releases](https://github.com/ook826092-cloud/naigen-app/releases/latest)

Le workflow s'exécute **uniquement sur déclenchement manuel** (Actions → Run workflow). Il va :
1. Auto-incrémenter la version PATCH (v2.0.0 → v2.0.1 → ...) et commiter dans le dépôt
2. Compiler un APK Release signé (avec un keystore)
3. Créer automatiquement une GitHub Release avec l'APK + la somme SHA256

## Correspondance des fonctionnalités du tutoriel

| Section tutoriel | Implémentation tutoriel | Cette App |
|---|---|---|
| §5.1 Vue d'ensemble Nai2API | Mode asynchrone Job `https://nai.sta1n.cn` | `NaiApiClient` + `NaiRepository.generate()` |
| §5.5.8 Routage de style `_get_artist_string` | Méthode Python | `StyleRegistry.resolveArtistString()` |
| §5.5.10 Flux asynchrone Job | `requests.post` + `time.sleep` polling | `client.createJob` + `delay()` + `client.pollJob` |
| §5.5.11 texte-vers-image | Méthode synchrone Python | `NaiRepository.generate()` fonction suspend |
| §5.5.13 29 styles communautaires | Fichier `nai_styles.json` | Constantes Kotlin `CommunityStyles.ALL` |
| §5.7.3 7 styles intégrés | dict `ARTIST_PRESETS` | `ArtistPresets.ALL` (avec chaînes artistes 7.12) |
| §5.7.4 `--variants N` concurrent | `ThreadPoolExecutor` | `generateVariants()` via `async{}.awaitAll()` |
| §5.5.4 détection auto de style | Table `AUTO_STYLE_KEYWORDS` | `AutoStyleKeywords.detect()` |
| §5.5.12 requête de solde | `GET /api/me` | `NaiRepository.checkBalance()` |
| §5.12 nommage fichier `YYYYMMDD_NNN.png` | `next_dated_output_path` | `ImageSaver.savePrivate()` |
| §5.6 7 préréglages intégrés | 2.5d/fresh/doujin/galgame/comicDoujin/animeOld/realistic_loli | identique |
| §5.8 9 options de taille | portrait/paysage/carré × standard/2K/4K | `SizeOptions.ALL` |
| §5.10 tous args CLI | `--steps/--scale/--cfg/--sampler/--negative/--artist` | Panneau « Paramètres avancés » |

## Stack technique

| Couche | Choix | Raison |
|---|---|---|
| UI | Jetpack Compose + Material 3 | UI déclarative moderne |
| Style visuel | Minimaliste iOS (blanc/gris + bordures fines + bleu iOS) | Propre, concentré |
| Asynchrone | Kotlin Coroutines + Flow | Remplace `time.sleep` + `ThreadPoolExecutor` Python |
| Réseau | OkHttp 4.12 + kotlinx.serialization | Remplace `requests` Python |
| BD | Room 2.6 (HistoryDao + FavoritesDao) | Historique + Favoris de prompts |
| Préférences | Preferences DataStore | Remplace `.env` Python |
| Chargement images | Coil 2.7 | Chargement ByteArray + sauvegarde galerie |
| Arrière-plan | Service de premier plan (type `dataSync`) | Survit à la mise en arrière-plan |
| Widget | AppWidgetProvider + RemoteViews | Widget de génération rapide |
| SDK min | Android 8.0 (API 26) | Couverture 95%+ des appareils |
| SDK cible | Android 15 (API 35) | Conformité service de premier plan Android 15/16 |

## Déploiement

### 1. Cloner & ouvrir dans Android Studio

```bash
git clone https://github.com/ook826092-cloud/naigen-app.git
```

Ouvrir dans Android Studio → attendre la fin de la synchronisation Gradle (télécharge ~500 Mo de dépendances au premier lancement).

### 2. Configurer le token Nai2API

Après installation de l'APK : ouvrir l'app → onglet Settings → remplir le token `STA1N-xxxxx…`.

Le token est stocké uniquement dans le DataStore privé de l'app — jamais uploadé nulle part.

### 3. Build APK Release

```bash
./gradlew assembleRelease
# Sortie : app/build/outputs/apk/release/app-release.apk (signé avec un keystore)
```

### 4. Installation

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Utilisation

### Génération basique

1. Ouvrir l'app, saisir un prompt en anglais dans l'onglet Generate
2. Choisir un style (défaut : 2.5d) — 36 préréglages dans l'onglet Styles
3. Choisir une taille (défaut : portrait)
4. Taper « Generate » — attendre ~30-40s
5. L'image s'affiche ; taper « Save to gallery » ou « Share »

### Variantes concurrentes

- Ajuster le nombre de variantes (1-6) dans le panneau « Style/Size »
- Soumettre N jobs indépendants simultanément, temps ≈ génération unique
- Choisir la meilleure

### Détection auto de style

- Activer « Auto style detection » dans le panneau Smart
- Le style change automatiquement selon les mots-clés du prompt (ex. « watercolor » → community:4)

### Paramètres avancés

- Déplier la ligne « Custom artist string »
- Surcharge possible : Steps / Scale / CFG / Sampler / chaîne artiste personnalisée

### Vérification du solde

- Bouton ⚡ en bas, ou Settings → Check balance

### Historique

- Onglet History : miniature + prompt + style + durée + horodatage
- Par item : Sauvegarder en galerie / Partager / Supprimer

### Favoris de prompts

- Onglet Favorites → « + » pour ajouter
- Sauvegarder des modèles de prompts réutilisables, marquer comme négatif si besoin

## Protection contre le bug de routage de style

Le tutoriel §5.5.17 avertit du bug « toutes les sorties ressemblent à 2.5d ». Cette app le prévient via :

1. **`StyleRegistry.resolveArtistString()`** implémente un routage complet à 3 niveaux :
   - ① Style communautaire `community:ID` → utilise `communityStyles.byId(id).artistString`
   - ② Préréglage intégré → utilise `artistPresets.get(key).artistString`
   - ③ Aucun des deux → retourne l'entrée telle quelle (traitée comme chaîne artiste personnalisée)

2. **`NaiRepository.generate()`** appelle toujours `resolveArtistString(styleKey)` pour traduire avant l'envoi à l'API

3. **Champ `customArtist`** (correspond à `--artist`) surcharge `styleKey` si non vide, atteignant la route ③

## Confidentialité et permissions

| Permission | Usage | Quand |
|---|---|---|
| INTERNET | Appeler Nai2API | Toujours |
| FOREGROUND_SERVICE_DATA_SYNC | Polling en arrière-plan | Au démarrage de GenerationService |
| POST_NOTIFICATIONS | Notifications de progression | Android 13+ au premier lancement |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Liste blanche batterie | Depuis la page keep-alive |
| READ_MEDIA_IMAGES | Sauvegarde galerie | Android 13+ uniquement |
| WRITE_EXTERNAL_STORAGE | Sauvegarde galerie | Android 9 et inférieur |

**Toutes les données utilisateur (token / historique / favoris / images) restent dans le stockage privé de l'app. Rien n'est uploadé.**

## Maintien en vie en arrière-plan

### Détection du fabricant

L'app détecte le fabricant via `Build.MANUFACTURER` + `Build.BRAND` + réflexion de propriété système. Couverture :

- Xiaomi / Redmi (MIUI)
- Huawei (EMUI / HarmonyOS)
- Honor (MagicOS)
- OPPO (ColorOS)
- OnePlus (fusionné dans ColorOS)
- vivo / iQOO (OriginOS / Funtouch OS)
- Samsung (One UI)
- Meizu (Flyme)
- Realme
- Android natif

### Flux du guide

L'onglet Keep-Alive va :
1. Afficher le fabricant détecté + astuces adaptées
2. Lister les éléments keep-alive nécessaires (autostart / batterie / popup arrière-plan / notifications)
3. Chaque élément a un bouton de saut — ouvre directement la sous-page de paramètres du fabricant
4. Afficher l'état courant (liste blanche batterie / permission notification) — rafraîchi automatiquement à la reprise
5. Repli : page de détails de l'app (fonctionne sur tous les ROMs)

### Adaptation Android 15 / 16

- `targetSdk = 35`
- `foregroundServiceType = dataSync` déclaré dans le Manifest
- `startForeground` avec surcharge de type (API 34+)
- Comportement `FOREGROUND_SERVICE_IMMEDIATE` + appel `startForeground` immédiat (règle des 6 secondes d'Android 16)
- Canaux de notification séparés : progression (LOW) + résultat (DEFAULT)

## Optimisations de performance

| Dimension | Optimisation | Emplacement |
|---|---|---|
| Réseau | Singleton OkHttp partagé, pool de connexions + HTTP/2 | `NaiApiClient.client` |
| Chargement images | ImageLoader Coil global, cache mémoire 25%, disque 50 Mo | `NaiApplication.newImageLoader()` |
| Chargement images | Downsampling inSampleSize pour miniatures, évite OOM | `ImageSaver.makeThumbnail()` |
| Compose | `@Immutable` sur StylePreset / GenRequest / GenResult etc. | `data/model/Models.kt` |
| Base de données | Index sur createdAt / styleKey / tag / isNegative | Classes Entity |
| Coroutines | Vérification `ensureActive()` dans la boucle de polling | `NaiRepository.generate()` |
| État | StateFlow + combine, `collectAsStateWithLifecycle` | `GenerateViewModel` |
| Notifications | `setOnlyAlertOnce(true)` + `setSilent(true)` | `buildProgressNotification()` |

## Dépannage

### Timeout de synchro Gradle (réseau Chine)

Dans `settings.gradle.kts`, remplacer `mavenCentral()` par les miroirs Aliyun :

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### Échec de génération : « API Token not configured »

Ouvrir Settings, remplir le token `STA1N-...`. Le token est masqué par défaut.

### Échec de génération : « HTTP 401 »

Token erroné ou expiré. Obtenez-en un nouveau auprès de votre fournisseur Nai2API.

### Timeout de polling (180s)

File d'attente serveur trop longue. Rechercher `MAX_POLL_TIME_MS` dans `NaiRepository.kt` et changer `180_000L` en `300_000L`.

### Erreur de schéma Room KSP

Nettoyer et rebuilder : `./gradlew clean` → `./gradlew assembleRelease`.

## Version

- v2.0.x (2026-07-15) — version courante. Voir [Releases](https://github.com/ook826092-cloud/naigen-app/releases) pour le changelog complet
- v1.0.0 (2026-07-15) — version initiale

## Licence

MIT — voir [LICENSE](LICENSE). Le service Nai2API et les liens d'achat sont tiers ; suivez leurs conditions d'utilisation.
