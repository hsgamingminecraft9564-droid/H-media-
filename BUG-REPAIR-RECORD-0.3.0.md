# H Media Player 0.3.0: bug repair record

**Verification note.** Nothing in this record was compiled, unit-tested or run on a phone in the
environment that repaired it. "Static checks" means the files were read and scanned (brackets,
XML, resource references, imports, translation keys, manifest references). Those checks pass.
A status of FIXED BUT NOT TESTED means the code change exists and no test has run on it.

**Statuses used:** FIXED AND TESTED, FIXED BUT NOT TESTED, REPRODUCED, NOT FIXED, NEEDS DEVICE TESTING, NOT IMPLEMENTED.

**Summary.** No item has FIXED AND TESTED or REPRODUCED, because no test or reproduction ran.
One defect was confirmed and repaired in this pass (BR-029). Most other items in the report were already
addressed in the code, and each is listed with its evidence. The rest need a Gradle build or a phone.

## 1. Project status items (report section 2)

| ID | Priority | Area | Status | Evidence and notes |
|---|---|---|---|---|
| BR-001 | Critical | APK compilation and clean build | NOT FIXED | Static checks pass (60 Kotlin files, 69 XML files, references, imports). No Gradle or Android SDK here, so nothing was compiled. The first GitHub Actions build is the real test. |
| BR-002 | Critical | VOID playback engine compilation and device testing | NOT FIXED | Same as BR-001. Engine unit tests (6 files) exist but have not run. |
| BR-003 | High | Background playback and service recreation | NEEDS DEVICE TESTING | `VoidPlaybackService.onGetSession` and `onTaskRemoved` exist (voidengine service, lines 28 and 30). Battery setting note in GITHUB-STEPS.md. |
| BR-004 | High | Video rotation and rendering | NEEDS DEVICE TESTING | Manifest uses configChanges. PlayerScreen skips pausing when the activity is only changing configuration. Picture now fits the video's shape (0.3.0). |
| BR-005 | High | Notification and lock-screen controls | NEEDS DEVICE TESTING | Engine service declared in voidengine manifest. Not verified on Android 13 or newer. |
| BR-006 | High | IPTV live playback | NEEDS DEVICE TESTING | M3U parsing has 9 unit tests (not run). No live stream has been played. |
| BR-007 | Medium | Theme system (120 themes) | NOT IMPLEMENTED | Specified in the theme engine summary. Four themes exist. |
| BR-008 | Medium | Translations (100 languages) | NOT IMPLEMENTED | Eight languages translated (`values-es`, `-fr`, `-de`, `-pt`, `-hi`, `-ne`, `-ru`, `-ar`). Ninety-two languages show English. |
| BR-009 | Medium | Privacy policy text | NOT IMPLEMENTED | Only English. |
| BR-010 | Medium | Automated and device test coverage | NOT FIXED | App unit tests: M3U parser, search parsing, search URL, web policy. Engine unit tests: 6 files. Instrumented: 1 (welcome gate). No tests for the playback queue, the home and library logic, or the theme system. |
| BR-011 | Medium | Release signing | NOT IMPLEMENTED | Debug build only. |
| BR-012 | Low | Search backend not used | NOT FIXED | Confirmed: `SearchResultsScreen` is not reachable from the shell. `SEARCH_API_BASE` is still in the build file. Left in place, documented, not removed. |

## 2. Previously identified bug areas (report section 3)

### Playback

| ID | Item | Status | Evidence and notes |
|---|---|---|---|
| BR-013 | Playback-state collectors keep running after Stop | FIXED BUT NOT TESTED | `Playback.releaseEngine()` cancels the collector scope before releasing the engine (Playback.kt, `scope?.cancel()`). `stop()` calls it. |
| BR-014 | End-of-track processed more than once | FIXED BUT NOT TESTED | `onEnded()` returns early when `endHandled` is set. The flag resets only when the state leaves ENDED. |
| BR-015 | Shuffle with an empty queue | FIXED BUT NOT TESTED | `toggleShuffle()` returns when the queue is empty. `rebuildOrder`, `advance` and `currentItem` are safe on an empty list. |
| BR-016 | Pause and rotation interfering with video | NEEDS DEVICE TESTING | PlayerScreen checks `isChangingConfigurations` before pausing or detaching. |
| BR-017 | Switching video and music must not pause audio | FIXED BUT NOT TESTED | The cleanup and background-stop code read the current media type through `rememberUpdatedState` (`isAudioNow`), not a value captured when the screen opened. |
| BR-018 | Speed labels must match the real speed | NEEDS DEVICE TESTING | The label shows `Playback.playbackSpeed`, which `setSpeed` sets. The engine keeps `lastSpeed` and re-applies it to each new backend (VoidEngineImpl lines 186-189, 298, 385). The app offers 1x to 2x; the engine accepts 0.25x to 4x, so nothing is clamped. Confirm on a phone. |

### Web browser

| ID | Item | Status | Evidence and notes |
|---|---|---|---|
| BR-019 | WebView destroyed correctly | FIXED BUT NOT TESTED | `DisposableEffect` calls `destroy()` on dispose (MainActivity.kt, lines 203-213). Leak not measured. |
| BR-020 | Recover from renderer crashes | FIXED BUT NOT TESTED | `onRenderProcessGone` (line 273) shows the crash message with a retry button (lines 146-154). |
| BR-021 | Page state across rotation | NEEDS DEVICE TESTING | `saveState` and `restoreState` (lines 186-199, 307). Configuration changes are handled without recreating the activity. |
| BR-022 | Loading, offline and error states | NEEDS DEVICE TESTING | Progress bar (line 221), error text with retry (lines 225-233), `onReceivedError` (line 267). Offline case not tested. |

### IPTV and playlists

| ID | Item | Status | Evidence and notes |
|---|---|---|---|
| BR-023 | Playlist state across rotation | FIXED BUT NOT TESTED | Channels are held in `IptvState` outside the screen (IptvScreen.kt, lines 28-30). Limitation: lost if Android kills the process. |
| BR-024 | Next and Previous channel behaviour | FIXED BUT NOT TESTED | The visible channel list is passed as the playback queue (IptvScreen.kt, lines 103-106). |
| BR-025 | Commas in channel names | FIXED BUT NOT TESTED | `titleOf` ignores commas inside quotes (M3uParser.kt, lines 13-24). Tests: `keepsCommasInTheChannelName` and the group-with-comma case (M3uParserTest.kt, lines 35-45). Tests not run. |
| BR-026 | Readable errors, not raw exceptions | FIXED BUT NOT TESTED | Errors map to string resources through `PlaylistError` (IptvScreen.kt, lines 68-71 and 135-168). |

### Home and media library

| ID | Item | Status | Evidence and notes |
|---|---|---|---|
| BR-027 | Recheck permissions after returning from settings | FIXED BUT NOT TESTED | `rememberResumeTick` in Home (line 72) and in the library screens. |
| BR-028 | Private Mode searches must not reach recent history | FIXED BUT NOT TESTED | `AppPrefs.remember` returns early in Private Mode (AppPrefs.kt, line 72). Home hides recent suggestions in Private Mode (Home.kt, line 143). |
| BR-029 | Incorrect empty-library state while loading | FIXED BUT NOT TESTED | **Confirmed defect, repaired in this pass.** The home screen showed "No files were found" while the lists were still loading. Fix: a `loaded` flag (Home.kt lines 78 and 95). `MediaRow` shows the loading text until `loaded` is true (line 223). The library screen already had this flag. |

## 3. VOID engine technical audit (report section 4)

| ID | Item | Status | Evidence and notes |
|---|---|---|---|
| BR-030 | VLC adapter API compatibility with libVLC 3.7.6 | NOT FIXED | Not compiled. `VERIFY_ON_FIRST_BUILD.md` lists the calls to check. |
| BR-031 | Media3 API compatibility and dependency alignment | NOT FIXED | Not compiled. Media3 1.10.1 in `voidengine`. |
| BR-032 | Service reconnection after the process is recreated | NEEDS DEVICE TESTING | `onGetSession` and `onTaskRemoved` exist. Not tested. |
| BR-033 | Notification-permission handling | FIXED BUT NOT TESTED | Shell.kt, lines 55-59: requested once on Android 13 and newer. |
| BR-034 | Picture-in-Picture integration | NOT IMPLEMENTED | `PictureInPicture.kt` exists in the engine. The app never calls it. |
| BR-035 | Tests for playback races and coroutine cancellation | NOT FIXED | Existing: `VoidEngineImplTest`. No new race or cancellation tests were added. |
| BR-036 | Vault truncation and corruption detection | FIXED BUT NOT TESTED | Truncated records throw `IOException` (VaultCrypto.kt, line 72). GCM failure throws `IOException` (line 101). The chunk index is bound as associated data (line 21). `VaultCryptoTest` exists, not run. |
| BR-037 | Backend selection, fallback and error reporting | NEEDS DEVICE TESTING | Media3 first, VLC fallback (`VoidEngines.createDefault`). `DefaultBackendSelectorTest` exists, not run. |
| BR-038 | Network reconnection without restarting manually paused playback | NEEDS DEVICE TESTING | `ReconnectPolicy` exists and has tests (not run). Paused-state behaviour not verified. |
| BR-039 | Audio focus, Bluetooth disconnect, headphone removal | NEEDS DEVICE TESTING | `AndroidAudioGuard` exists in the engine. Not tested on a device. |
| BR-040 | Is the native backend scaffolding? | NOT IMPLEMENTED | **Confirmed scaffolding.** `NativeBackendAdapter` is not selected by `BackendSelector`, and its `attachVideoView` does nothing. Documented, not removed. |

## 4. Repair plan status (report section 5)

| Phase | Status | Notes |
|---|---|---|
| 1. Build stability | NOT FIXED | Static checks pass. Gradle build not run here. Module names confirmed: `:app` and `:voidengine`. |
| 2. Playback engine | NEEDS DEVICE TESTING | Queue and lifecycle fixes listed above. Engine adapters not compiled. |
| 3. App functionality | NEEDS DEVICE TESTING | Home loading defect repaired (BR-029). Other items listed above. |
| 4. Android integration | NEEDS DEVICE TESTING | Foreground service and notification request in place. Lock screen and PiP not verified. |
| 5. Themes and design | NOT IMPLEMENTED | Deliberately not started (report section 9). |
| 6. Quality assurance and release | NOT FIXED | No device tests run. No release signing. |

## 5. Build verification commands (report section 6)

The report's commands use `./gradlew`. That needs the executable bit. In the zip the bit is not set, so run
`sh gradlew` instead. The module names in the commands match the project (`:app`, `:voidengine`). Status: NOT FIXED (not run).

## 6. Release checklist (report section 8)

| Item | Status |
|---|---|
| Clean APK build succeeds | NOT FIXED |
| Unit tests pass | NOT FIXED |
| Instrumented and emulator tests pass | NOT FIXED |
| Audio and video playback work | NEEDS DEVICE TESTING |
| Background playback works | NEEDS DEVICE TESTING |
| Notifications and lock-screen controls work | NEEDS DEVICE TESTING |
| IPTV playback works with supported playlists | NEEDS DEVICE TESTING |
| Search and browser work | NEEDS DEVICE TESTING |
| No reproducible critical crashes or major leaks | NOT FIXED |
| Theme selection and previews work | NOT IMPLEMENTED |
| Language switching works for the languages marked complete | NEEDS DEVICE TESTING |
| Release APK signed and install-tested | NOT IMPLEMENTED |

## 7. Historical notes (not current defects)

- The learner, the engine presets and the smart engine switch were removed with the old engine (0.2.0).
- The old search results screen existed in 0.2.0. It is not reachable in 0.3.0.
- Earlier documents described these removed features. They were corrected in 0.3.0.

## 8. Known limitations after this repair

- Nothing has been compiled or run on a phone in this environment.
- Two M3U parsers exist: the app's and the engine's (`voidengine/.../features/iptv/M3uParser.kt`). They should be merged.
- The IPTV list survives rotation but not process death.
- The web browser's offline behaviour is not tested.
