# VOID Engine

A drop-in Android library module (`:voidengine`) that gives H Media Player its own playback engine API
with VLC and Media3 behind it. Your UI talks only to `VoidEngine`; backends are swappable.

## What is in here (by phase)

| Phase | Where |
|---|---|
| 1 Audit and protect | `tools/phase1_audit.sh`, `docs/PHASE1_AUDIT.md` |
| 2 Engine API | `PlaybackSource`, `PlayerSnapshot`, `PlaybackState`, `PlaybackError`, `VoidEngine`, `BackendAdapter` |
| 3 Backend adapters | `backend/VlcBackendAdapter`, `backend/Media3BackendAdapter`, `BackendSelector` (with explicit fallback) |
| 4 Reliability | `VoidEngineImpl` (serialized, generation guard, one cancellable reconnect job), `reliability/AndroidAudioGuard` (modern audio focus, ducking, headphones unplugged) |
| 5 Features | `service/` (MediaSession notification, background, PiP), `features/subtitles`, `equalizer`, `lyrics`, `iptv`, `epg`, `url`, `vault` |
| 6 Tests | `src/test` (engine, selector, parsers, vault crypto), `tools/static_check.py` |
| 7 Native components | `nativecore/FormatDetector` (first replaceable component), `NativeBackendAdapter` extension point, `docs/PHASE7_NATIVE.md` |

## Install

1. Unzip next to your app module, add `include(":voidengine")` to `settings.gradle.kts`.
2. In your app: `implementation(project(":voidengine"))`.
3. Pick your real package name (the engine package must not contain Java keywords like `void`):
   `python3 tools/rename_package.py com.hmedia.player.engine`
4. If your project uses AGP 9 with built-in Kotlin, delete the `org.jetbrains.kotlin.android` line in `voidengine/build.gradle.kts`.
5. Sync, then run the unit tests: `./gradlew :voidengine:testDebugUnitTest`.
6. Read `docs/VERIFY_ON_FIRST_BUILD.md` (a short list of the API calls I could not compile-check here).

## Use

```kotlin
// Application.onCreate (or your DI module)
val engine = VoidEngines.createDefault(this)          // Media3 first, VLC fallback, audio focus included

// Player screen
engine.attachVideoView(surfaceView)                   // call detachVideoView() in onStop()
engine.open(PlaybackSource.fromUri(uri, title = "Movie"))   // setSource + prepare + play
lifecycleScope.launch { engine.snapshot.collect { render(it) } }

// Background playback + notification + lock screen (needs POST_NOTIFICATIONS on Android 13+)
val connector = PlaybackServiceConnector(this); connector.connect()

// Direct URL / IPTV / EPG / lyrics / subtitles
SourceClassifier.fromUserInput(text)?.let { engine.open(it) }
M3uParser.parse(playlistText).first().toSource().let { engine.open(it) }
val guide = XmltvParser.parse(stream); guide.nowAndNext(channel.tvgId!!, System.currentTimeMillis())
val lyrics = LrcParser.parse(lrcText); engine.snapshot.map { lyrics.indexAt(it.positionMs) }.distinctUntilChanged()
engine.open(PlaybackSource.of(url).copy(externalSubtitles = listOf(SubtitleFormats.external(srtUri, "en"))))

// Equalizer: read bands from the snapshot, then
engine.setEqualizer(EqualizerPresets.forName(EqualizerPresets.BASS_BOOST, engine.snapshot.value.equalizerBandsHz.size))

// Private Vault (encrypted at rest, streams without writing plaintext)
val vault = PrivateVault(File(filesDir, "vault"), VaultCrypto({ VaultKeys.getOrCreate("h_vault") }))
val engine = VoidEngines.createDefault(this, VaultDataSourceFactory(vault))
val entry = vault.addFile("holiday.mp4", contentResolver.openInputStream(uri)!!)   // do this off the main thread
engine.open(vault.sourceFor(entry))
```

## Design rules you can rely on

* UI never touches VLC/Media3. `grep -r "org.videolan" app/src` should only match `VlcBackendAdapter.kt`.
* All engine work is serialized on the main thread; read results from `snapshot`.
* Pause, stop and permanent focus loss never restart playback. Reconnect happens only for remote streams that dropped while the user wanted playback, max 5 attempts, cancelled by pause/stop/setSource.
* The only automatic backend switch is before a source ever played (e.g. Media3 cannot parse an AVI, so VLC takes over). It shows up in `snapshot.backendName`.
* Vault: AES-256-GCM in 1 MiB chunks, key in Android Keystore, chunk index bound as AAD. Biometric gating is up to your UI (`VaultKeys.getOrCreate(alias, authValiditySeconds)` + BiometricPrompt).

## Known limits (honest list)

* libVLC cannot send arbitrary HTTP headers; only User-Agent and Referer are forwarded. Media3 sends all of them.
* The vault does not detect removal of whole chunks from the end of a file.
* Phase 7 is scaffolding: the native backend is a placeholder until you implement and test a component.
* Equalizer availability and band count depend on the device when Media3 is the backend.
