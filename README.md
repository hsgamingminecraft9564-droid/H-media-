# H Media Player (build 0.2.0)

Open the folder in Android Studio, or push it to GitHub. The workflow in
`.github/workflows/build.yml` builds `app-debug.apk`: open the Actions tab, pick the run,
then download the file from Artifacts. See `GITHUB-STEPS.md` for the full steps.

## In this build
- Welcome screen with the castle background, a readable privacy policy, consent (required before continuing), Guest, and Private Mode
- App icon: your H logo with the red play triangle, at all five screen densities
- Home with global search: finds video and audio on the phone (asks for media permission first),
  autocompletes from recent searches, and runs a web search through the H Search backend
- Results list from the backend; pages open in the app's own viewer. Private Mode clears web cookies and cache on exit
- Player on the Void engine: libVLC underneath, 14 presets in Settings, a progress watchdog,
  weak-network boost, automatic reconnects, software-decoding fallback, and a learner that picks presets on the phone
- Background music: playback runs in a foreground service with a media notification (play, pause, stop)
  and lock-screen controls. Music keeps playing with the screen off or another app open
- Video pauses when you leave its screen or the app goes to the background; music does not
- Finished files stop and offer Play again. A web video that ends is not reconnected; a stream that drops is
- Audio focus: music pauses for phone calls and other apps, and pauses when headphones are unplugged
- A now-playing strip on the home screen shows what is playing and opens the player
- Video and music: progress bar and time, seek, a music screen for audio files, and the screen stays on during video
- Rotating the phone keeps playback where it is
- Settings: four themes, four fonts, 21 language choices (translations fall back to English), Void engine presets,
  smart engine switch, reset of what the engine learned, Private Mode switch, clear recent searches

## Supported Android versions
Android 7.0 (API 24) and newer. Built and targeted for Android 15 (API 35). Not yet tested on a device.

## Supported media addresses
- Network: http, https, rtsp, rtmp, udp, mms (whether each plays depends on the libVLC build; to be tested)
- Phone storage: content (Android media index) and file
- Anything else is refused with a message.

## Tests
- Android unit tests: `sh gradlew :app:testDebugUnitTest` (run automatically in CI). They cover the search address rules,
  the web page rules, the reconnect rules, the media address rules, the playback presets and search parsing.
- Emulator tests: the "Device tests (emulator)" workflow, run by hand.
- Real phone: DEVICE-TEST-PLAN.md (35 cases).
- Search backend: `node --test search-server/test/lib.test.mjs` (Node 20 or newer).

## Not in this build yet
- Sign-in with Google, Facebook, or email (the button is shown but disabled)
- Headset media buttons (play/pause on a wired or Bluetooth headset)
- Playlists and next/previous track
- IPTV playlist viewer
- Picture viewer
- Translated strings for each language, the dictionary, and the theme store
- The full medieval font file and 100+ font files (the medieval style uses the system serif font for now)
- Settings screen layout matching your Settings design (no Settings screenshot received yet)

## Before the first run
1. Deploy `search-server/` behind HTTPS and set the repository variable `SEARCH_API_BASE` (see GITHUB-STEPS.md).
   Until then the results screen shows "Search server not set up yet."
2. Allow notifications for the app when asked (Android 13 and newer). Without it, music still plays but the
   notification and lock-screen controls stay hidden.
3. On some phones (Xiaomi, Redmi, Oppo and others), set the app's battery usage to "No restrictions",
   or the system may stop music when the screen is off.
4. The project has not been compiled or run on a phone yet. The first build may show errors, so send the log if it does.
