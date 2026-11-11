# HOME-SLICE2 fixes

Not compiled or run: no Kotlin compiler or Android SDK was available when this was prepared.
Build it in your project and run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

## Would not compile
- `Shell.kt`, `Home.kt`, `Library.kt`, `IptvScreen.kt`: `rememberSaveable` was used without
  `import androidx.compose.runtime.saveable.rememberSaveable`.
- `MainActivity.kt`: removed the unused old `HMediaApp`, `HomeScreen` and `Screen`. They referenced
  `SearchResultsScreen`, which is not in this slice. `AppShell` replaced them.
- `MainActivity.kt`: removed `BuildConfig.DEBUG` (needs `buildFeatures.buildConfig = true` on AGP 8+);
  debug detection now uses the app's debuggable flag.
- `Playback.kt`: removed `import kotlinx.coroutines.flow.collect` (unresolved on coroutines 1.6+).

## Runtime bugs
- Playback: each engine's collector coroutine was never cancelled, so after Stop it kept overwriting
  state. One "ended" event could be handled twice and skip a track. Shuffle with an empty queue.
- Player: rotating paused video (it now keeps playing). Leaving the player after the queue moved from
  video to music paused the music (stale `isAudio`). Speed label disagreed with the engine.
- Player: landscape pushed the video to almost no height; now side-by-side. Text was dark on the dark
  overlay in light themes. Artwork flashed blank between tracks; now crossfades.
- Web: WebView was never destroyed (leak). A renderer crash left a dead view; now shows Retry.
  Page and history lost on rotation; now restored. Added loading bar, load-error message with retry,
  download links hand off to the phone browser.
- IPTV: loaded playlist lost on rotation. Channels played one at a time (Next/Previous did nothing);
  the shown channels are now the queue. Errors showed raw hostnames; now clear, translatable messages.
- M3U: a comma in a channel name cut it short ("Sports, Live HD" became "Live HD"). Commas inside
  quoted attributes. Byte-order mark.
- Library/Home: media access was never re-checked, so granting it in system settings had no effect.
  "No files" flashed while loading. Home showed recent searches in Private Mode.
- Shell: notification permission was requested again on every rotation. Back from a tab left the app.
  Landscape and wide screens now use a scrolling side rail instead of a bottom bar.
- Welcome screen clipped its buttons in landscape; now scrolls.
- Media search: the SQL `LIKE ... ESCAPE` selection is replaced by filtering in Kotlin.
- Category chips use DuckDuckGo's full tab parameters.

## Tests
`M3uParserTest` grows from 3 to 9 cases. The parser logic was also run against these cases in a Java
port; all passed.

## Still needed from your project (not part of this slice)
`AppPrefs`, `HMediaTheme`/`HTheme`/`HFont`/`LANGUAGES`, `com.hmedia.player.web.WebPolicy`,
`R.drawable.welcome_bg`, the `com.hmedia.voidengine` module, the manifest and Gradle files.
