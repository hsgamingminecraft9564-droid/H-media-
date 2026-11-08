# H Media Player: changelog (final build 0.2.0)

## Playback and engine
- Playback is owned by one object that lives as long as the app, so music keeps playing when the screen is off or another app is open.
- Background playback runs in a foreground service with a media notification (play, pause, stop) and lock-screen controls.
- Music pauses for phone calls and other apps (audio focus), and when headphones are unplugged.
- Video and streams pause when you leave the player or the app goes to the background. Music keeps playing.
- Finished files stop and offer Play again from the beginning.
- A web video that reaches its end is no longer reconnected in a loop.
- A stream that drops early is reconnected with growing waits. Retries reset only after 10 seconds of stable playback.
- Play button, notification and home strip show the real state after finish, error, and stop.
- Late player events after stop cannot bring the notification back.
- Progress bar, time display and seek; music shows a "Now playing" screen; the screen stays on during video.
- Rotation and dark-mode changes keep playback where it is.
- Weak-network boost, freeze detection, software-decoding fallback, and the learner are kept.
- Fixed the video output setting that could crash on some phones.
- Learner ignores damaged saved data instead of crashing.

## Search and media
- Local media search skips a media type the phone has not granted, instead of crashing.
- Typed % and _ are searched as plain text.
- Search waits for typing to pause (250 ms) before running.
- Network problems show plain sentences, not technical text.
- Search results open in the app's own viewer.
- Web viewer: only web addresses load; links to other apps are blocked; file and content access are off; Private Mode clears cookies, cache and web storage on leaving.

## Search backend (search-server)
- Refuses to start without a valid provider and says why.
- Health check at /health.
- Timeouts on search provider calls (8 seconds).
- HTML tags and entities removed from snippets.
- Only http and https links are returned.
- Memory for rate-limit tracking is pruned.
- Tracking parameters removed from result links; queries are not logged.

## Theme, font and look
- Font setting fixed: Material 3 has no default-font parameter, so the chosen font is applied to every text style.
- Dark window background from the first frame (no white flash).
- Content stays clear of the status and navigation bars (Android 15 edge-to-edge).
- Launcher icon: the H logo with the red play triangle, at all five densities.

## Privacy
- Readable privacy policy dialog on the welcome screen (the consent text refers to it).
- Consent is required before the app continues.

## Build and project
- Kotlin 2.2.10, Android Gradle Plugin 8.9.1, compile and target SDK 36 (Android 16).
- libVLC 3.7.6 (current Maven Central release).
- Build workflow pins Gradle 8.11.1 and has least-privilege permissions.
- Lock-screen media controls via androidx.media 1.7.0 (MediaSession).
- Removed an unused second search client that bypassed the backend.
- Removed unused strings, constants and imports.
- .gitignore keeps build output, local settings and signing keys out of GitHub.

## Developer review round
- Engine: one cancellable retry; pause, play, open, preset switch and release cancel it.
- Engine: audio focus uses AudioFocusRequest on Android 8 and newer.
- Engine: VLC events are handled on the main thread.
- Protocols: one shared list (MediaClassifier) used everywhere; unsupported addresses are refused with a message.
- Private Mode: history suggestions hidden with a note; playback not recorded for the learner.
- Permissions: media access asked only when results are requested, with the reason first; settings link after permanent refusal; notification permission asked when music first plays.
- Web viewer: Safe Browsing (androidx.webkit 1.14.0); certificate errors cancelled; "Scripts on web pages" setting.
- Search: the address comes from build settings (SEARCH_API_BASE), never from the source; "No results" message.
- Search backend: logic moved to lib.mjs with 7 unit tests; Dockerfile includes it.
- Build: unit tests and lint in CI; lint report saved; search address set at build time.
- Docs: DEVELOPER-STATUS.md maps every review item to its status.

## Second developer review round
- Search address: strict URL checks (https, host, no spaces, username, password, query or fragment) with clear reasons; the query is encoded. Tests.
- Web pages: one rule for which pages may open (plain http and https only, no username or password). Scripts cannot open windows automatically; location is off; mixed content is never allowed. Tests.
- Media addresses: scheme names are compared without case or spaces. Tests.
- Reconnect: the decisions moved to ReconnectPolicy (delay grows to one minute; a user pause or a finished file never reconnects; retries count only after ten seconds of stable play). Tests.
- Gradle: gradlew bootstrap for Linux and macOS, and gradlew.bat for Windows. Downloads the pinned Gradle 8.11.1 and checks its SHA-256 first. Tested against a fake distribution.
- Device testing: DEVICE-TEST-PLAN.md (35 cases) for real phones; an emulator workflow with one instrumented test for the welcome gate.
- DEVELOPER-STATUS.md updated for every item.

## Still open (not in this build)
- Sign-in (Google, Facebook, email): not built.
- Headset play and pause buttons: not wired.
- Playlists and next/previous track.
- IPTV viewer, picture viewer, full medieval font file, translations beyond English.
- Not yet compiled: the first GitHub build may show errors. Send the failed log lines.

## Round: protocol, URL, reconnect, WebView and test work
- Network streams reconnect before the software decoder is tried (fixes a fallback that was spent on a dropped connection).
- Media schemes are matched without case and without surrounding spaces.
- The search server address is checked in code and at build time (https, host name, no path, query or user name).
- WebView: pop-ups and permission requests refused; file-URL access off; web storage off in Private Mode; debugging only in debug builds; renderer crashes handled.
- Gradle: `gradlew` and `gradlew.bat` download the pinned Gradle version and verify its checksum; no wrapper jar needed.
- Device tests: `WelcomeGateTest` (emulator), `device-test.yml`, and `DEVICE-TEST-PLAN.md` for real phones.
