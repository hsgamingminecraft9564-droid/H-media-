# Developer review: status of each item

Each item says what was changed, and what still needs a person with a phone, a server, or a build machine.
"Done" means the change is in the code and was checked as far as possible without those.

## Priority 1: build and release
| Item | Status | Notes |
|---|---|---|
| Gradle wrapper | Done (bootstrap) | `gradlew` (Linux and macOS, run with `sh gradlew`) and `gradlew.bat` (Windows, untested) download the Gradle version pinned in `gradle/wrapper/gradle-wrapper.properties` and refuse to run it unless the SHA-256 matches. Tested here against a fake distribution: download, cache, wrong checksum, missing checksum. To replace it with Gradle's own wrapper jar, run `gradle wrapper --gradle-version 8.11.1` once on a machine with Gradle. |
| CI builds the debug APK | Done | `.github/workflows/build.yml`. |
| Unit tests and lint | Done in CI | Unit tests run on every build and must pass. Lint runs and its report is saved, but does not block the build yet. |
| Fix build errors and warnings | Not verified | No Android build was possible here. The first CI run is the real test. |
| Version alignment | Done | 0.2.0 (versionCode 2) in the build file and all documents. |
| Build logs and APK artifact | Done | The APK and the test and lint reports are saved as CI artifacts. |

## Priority 2: search backend
| Item | Status | Notes |
|---|---|---|
| Deploy to a real HTTPS endpoint | Not done here | Needs a server, a domain and a provider key. See `search-server/README.md`. |
| Configure the app | Done | The address comes from the `SEARCH_API_BASE` repository variable or `-PsearchApiBase`. |
| URL validation | Done | `search/SearchUrl.kt` accepts only https addresses with a host, no spaces, no username or password, no query or fragment, under 300 characters. The placeholder address is reported as "not set up yet". The query is encoded. Unit tests: `SearchUrlTest`. |
| Keys stay on the server | Done | The server reads keys from its environment. A secret scan of the project found no keys. |
| Validate requests, handle errors and timeouts | Done | Server: empty and long queries refused, 8-second upstream timeout, 429 on rate limit. App: connect and read timeouts, plain error messages. |
| Loading, empty, offline and error states | Done | Loading indicator, "No results for ..." text, offline sentence, server message. |
| Tests for successful and failed responses | Done | Android: `SearchParseTest`. Server: `search-server/test/lib.test.mjs` (7 tests, passing here). |
| Works on a real device | Not verified | Needs the deployed server and a phone. See DEVICE-TEST-PLAN.md (D05 to D10). |

## Priority 3: playback engine: pause and reconnect
| Item | Status | Notes |
|---|---|---|
| Audio focus on Android 8+ uses AudioFocusRequest | Done | The older call remains only for Android 7, the minimum version. |
| Reconnect decisions | Done | `engine/ReconnectPolicy.kt` decides whether to reconnect, when to give up, how long to wait (2 seconds, growing, capped at 1 minute), and when a retry counts as stable. Unit tests: `ReconnectPolicyTest`. |
| Pause, play, open, preset switch and release cancel a waiting retry | Done in code | Only one retry can wait at a time. Verified by reading the code, not by running it. |
| A paused stream never restarts by itself | Done in code | Pause cancels a waiting retry. A retry that does fire checks for a user pause and a released engine again. Device test D28 and D29. |
| VLC operations on one thread | Partly | Every engine call and every VLC event runs on the main thread. Whether libVLC is safe on this path needs a device test. |
| UI state changes on the main thread | Done | VLC events are posted to the main looper before they change state. |
| Stale events from old media | Partly | Events from a replaced player are ignored. Filtering per media item needs a device test. |
| Calls, notifications, Bluetooth, unplug, lock | Not done here | Device tests D19 to D26. |

## Priority 4: media protocol classification
| Item | Status | Notes |
|---|---|---|
| One shared list of schemes | Done | `engine/MediaClassifier.kt` is the only list. The engine and the playback code both use it. Scheme names are compared without case or spaces. |
| Document supported schemes | Done | README, "Supported media addresses". |
| Refuse unsupported or malformed addresses clearly | Done | The player does not start, and a message says why. Unit tests: `MediaClassifierTest`, `MediaClassifierCaseTest`. |
| RTMP and MMS actually play with the shipped libVLC | Not verified | Depends on the libVLC build. Needs a test with real addresses. |

## Priority 5: WebView JavaScript security
| Item | Status | Notes |
|---|---|---|
| JavaScript only where needed | Partly | "Scripts on web pages" setting, on by default. Choosing per page is not possible. |
| Validate schemes and hosts before navigation | Done for schemes, partly for hosts | `web/WebPolicy.kt`: only plain http and https addresses with a host, no username or password, no spaces, under 2048 characters. Hosts are not restricted, because results can be any site. Unit tests: `WebPolicyTest`. |
| Block unsafe schemes and external navigation | Done | Other schemes (intent, market, file, content, data, javascript) are blocked. |
| Safe Browsing | Done | `androidx.webkit` 1.14.0; enabled where the WebView supports it. |
| Popups, location and mixed content | Done | Scripts cannot open windows automatically, location is off, and mixed content is never allowed. |
| No JavaScript interfaces for untrusted pages | Done | None are added. |
| Never proceed through certificate errors | Done | Certificate errors cancel the load. |
| Test malicious, malformed and redirected URLs | Partly | The address rules are unit tested. Real pages and redirects need a device test (D11). |

## Priority 6: Private Mode
| Item | Status | Notes |
|---|---|---|
| Hide history suggestions | Done | No suggestions are shown in Private Mode, and a note says so. |
| Do not save searches, playback history or metadata | Done | Searches are not stored, and playback is not recorded for the learner. |
| Clear temporary private data when the session ends | Partly | Web cookies, cache and storage are cleared when you leave a web page. Private Mode is a setting that stays on, so there is no session end to hook into. |
| Test enable, disable and restarts | Not done here | Device tests D13 and D14. |

## Priority 7: permissions
| Item | Status | Notes |
|---|---|---|
| Ask only when a feature needs them | Done | Media access is asked when you request results from this phone. Notification permission is asked when music first plays. |
| Correct permissions for each Android version | Done | Unchanged. |
| Explain before asking | Done | The reason and an "Allow media access" button appear first. |
| Handle denial and permanent denial | Done | After a second refusal, the app offers "Open app settings". |
| Test on Android 10 to 15 | Not done here | Device tests D04 to D06. |

## Priority 8: documentation and feature status
| Item | Status | Notes |
|---|---|---|
| Consistent version | Done | 0.2.0 everywhere. |
| Mark unfinished features | Done | README, "Not in this build yet". |
| Do not advertise unbuilt features | Done | Sign-in, headset buttons, playlists, IPTV, pictures, translations and the theme store are listed as not built. |
| Supported Android, build steps, limitations | Done | README and GITHUB-STEPS. |

## Testing: what runs where
| Test | Where it runs | Status |
|---|---|---|
| Android unit tests (search URL, web policy, reconnect policy, media classifier, presets, search parsing) | CI on every build (`gradle :app:testDebugUnitTest`) | Written, not yet run. The first CI run is the test. |
| Welcome gate, emulator test (`WelcomeGateTest`) | Device-test workflow, started by hand | Written, not yet run. |
| Search backend unit tests (`search-server/test/lib.test.mjs`) | Any machine with Node 20 or newer | Passing here. |
| Bootstrap `gradlew` | Any Linux or macOS machine | Passing here, against a fake distribution. |
| Real phone | DEVICE-TEST-PLAN.md | Not done here. 35 cases, to be run by a tester. |

## Suggested order for the team
1. Run the first CI build and send the log of any failure.
2. Deploy `search-server/` over HTTPS and set `SEARCH_API_BASE`.
3. Run the device-test workflow once. Then run DEVICE-TEST-PLAN.md on real phones, starting with D01 to D10, then playback (D15 to D30), then Private Mode, permissions and the web viewer.
4. On a machine with Gradle, run `gradle wrapper --gradle-version 8.11.1` and commit the generated files.

## Update: the six open items
| Item | Status | What changed | Still needs |
|---|---|---|---|
| Playback pause and reconnect handling | Done in code | A network stream that drops reconnects first; the decoder is changed only after repeated failures (`FailurePolicy`, tested). Reconnect rules live in `ReconnectPolicy` (tested). Pause cancels a waiting retry. A retry counts only after 10 seconds of stable play. | Device tests: calls, Bluetooth, headphone unplug, lock screen (`DEVICE-TEST-PLAN.md`). |
| Search API URL validation | Done | `SearchUrl.checkBase` rejects non-https, spaces, user names, queries, paths and bad host names, with the messages in `SearchUrlTest`. The build fails early if `SEARCH_API_BASE` is malformed. | A real deployed server. |
| Media protocol classification | Done | One shared list (`MediaClassifier`). Schemes are compared without case and without surrounding spaces (`MediaClassifierCaseTest`). | Confirm that each protocol plays with the shipped libVLC (device test). |
| WebView JavaScript security | Done, with one choice left | Pop-up windows and permission requests (camera, microphone, location) are refused. File-URL access and mixed content are blocked. Web storage is off in Private Mode. Remote debugging is on only in debug builds. A crashed renderer does not close the app. Only http and https pages open (`WebPolicy`). Certificate errors are cancelled. | JavaScript stays on by default, because most result pages need it. The Settings switch turns it off. Per-page control is not possible. |
| Missing Gradle wrapper files | Done, not run here | `gradlew` (sh), `gradlew.bat` and `gradle/wrapper/bootstrap.ps1` download the Gradle version pinned in `gradle-wrapper.properties`, check its SHA-256 (f397b287…, Gradle's published hash for 8.11.1), and run it. No wrapper jar is needed. | Run `sh gradlew :app:assembleDebug` once on a machine with internet to confirm it. On Linux or macOS, if the file loses its execute bit after a web upload, run `git update-index --chmod=+x gradlew`. |
| Real Android device testing | Partly | `WelcomeGateTest` (instrumented test), `device-test.yml` (emulator, started by hand), `DEVICE-TEST-PLAN.md` (phone checklist). | Run them: no device or emulator run has happened yet. |
