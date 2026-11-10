# Verify on first build

This code was written and reviewed without an Android SDK or Kotlin compiler available, so it has NOT been compiled.
What was checked: bracket balance, package/directory match, internal imports, that every adapter implements every
interface member (`tools/static_check.py`), and the vault chunk arithmetic (simulated). The unit tests were written but not run.

Run: `./gradlew :voidengine:testDebugUnitTest :voidengine:assembleDebug`

If the build fails, these are the places most likely to need a one-line fix (library API details I could not verify):

| File | Call | Note |
|---|---|---|
| `VlcBackendAdapter` | `setSpuTrack`, `getSpuTracks`, `getCurrentVideoTrack`, `IMedia.Slave(...)`, `MediaPlayer.Equalizer.*` | libVLC 3.7.x names; compare with the VLC-Android sample |
| `VlcBackendAdapter` | `p.setMedia(media)` | if your libVLC version wants `IMedia`, a cast is enough |
| `VoidPlayer` | `SimpleBasePlayer` overrides and `State.Builder` setters | Media3 1.10.x |
| `Media3BackendAdapter` | `setOverrideForType`, `DefaultMediaSourceFactory(DataSource.Factory)` | |
| `voidengine/build.gradle.kts` | Kotlin plugin line, versions | Media3 1.10.1, libvlc-all 3.7.6 (3.x API, not 4.0 EAP) |
| tests | `advanceTimeBy` import | kotlinx-coroutines-test 1.10.x |

Runtime checks on a real device: pause then lock screen (no restart), phone call (pauses, resumes after),
unplug headphones (pauses), Bluetooth disconnect, kill network on an HLS stream (reconnects, then gives up after 5),
AVI/WMV file (Media3 fails, VLC takes over), vault video seek, notification controls on Android 13+.
