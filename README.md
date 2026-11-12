# H Media Player (version 0.3.0)

An Android media player for your own videos and music, with internet search in its own viewer,
IPTV from a playlist you supply, and the VOID playback engine.

## What the app does
- **Home:** internet search bar with All, Images, Videos and News options, recent searches,
  banner cards with thumbnails of your videos and music, recently played items, and shortcuts.
  Recent searches are not saved in Private Mode.
- **Video and Music libraries:** built from the files on your phone after you allow media access.
  Filter by name. Tapping a file plays the list in order, so Next and Previous work.
- **Player:** the picture fits the shape of the video (widescreen, portrait or square), with
  black bars where needed. Previous, play/pause, next, shuffle, repeat (off, all, one), speed
  (1x, 1.25x, 1.5x, 2x), and seek. Music shows album art on a blurred background where the phone has it.
- **IPTV:** you type the address of an M3U playlist you are allowed to use. Nothing is bundled.
- **Internet search:** DuckDuckGo pages inside the app. Links to other apps are blocked. Private
  Mode clears cookies, cache and web storage when you leave a page.
- **Settings:** four themes, four fonts, a language list of 100 languages (translated into eight;
  the rest show English), and privacy options: scripts on web pages, Private Mode, clear recent searches.
- **Background playback:** the VOID engine runs a playback service with a media notification.
  Confirm this on your phone; see DEVICE-TEST-PLAN.md.

## Playback engine
The app uses the **VOID engine** (module `voidengine`). The app talks only to the `VoidEngine`
interface. Media3 plays first and libVLC is the fallback. The engine also handles audio focus,
reconnects for streams, and includes parsers for M3U, XMLTV and LRC, and an encrypted private vault.
The app does not yet show EPG, lyrics or the vault.

## Build
- **In GitHub:** the "Build APK" workflow runs on every push. Download `H-Media-Player-debug` from
  the run's Artifacts to get `app-debug.apk`.
- **On a computer:** `sh gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`).

Versions: Android Gradle Plugin 8.9.1, Kotlin 2.2.10, Gradle 8.11.1, Compose BOM 2024.09.00,
minimum Android 8.0 (API 26), target and compile SDK 36, AppCompat 1.7.0, libVLC 3.7.6, Media3 1.10.1.

## Not yet verified
- The version 0.3.0 build has not been compiled in the environment where it was written.
  The first GitHub build is the real test.
- Device behaviour: video fit, thumbnails, background playback, IPTV streams, language switching.
- Translations: eight languages need review by a native speaker. The privacy policy text is English.

## Known limits
- Thumbnails need Android 10 or newer; older versions show the banner.
- Search server (`search-server/`) is not used by the current screens.
- The theme engine is specified (see the theme engine summary) but not built yet.

## Documents
- CHANGELOG.md: what changed in each version.
- DEVELOPER-STATUS.md: what is done, partly done, or not verified.
- DEVICE-TEST-PLAN.md: 35 test cases for a real phone.
- GITHUB-STEPS.md: from GitHub to an installed APK.
- docs/voidengine/: the engine's design, integration notes and verification list.
- CHANGES-HOME-SLICE2.md and CHANGES-ICONS-THUMBS-LANG.md: the last two sets of changes.
