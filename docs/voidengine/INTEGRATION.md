# VOID engine inside H Media Player

## What changed
- The engine is the Gradle module `:voidengine`, package `com.hmedia.voidengine`.
- `Playback.kt` now plays every file through the engine. Playback, pause, seek and state come from `engine.snapshot`.
- `PlayerScreen.kt` shows music (artwork and controls) or video (the picture from `attachVideoView`).
- Background music and the lock-screen controls come from the engine's own service, `VoidPlaybackService`.
- Our old `PlaybackService` is no longer declared in the manifest. Its source file was removed.
- The minimum Android version is now 26 (Android 8.0), because the engine requires it.

## Not changed yet
- The old engine (our own libVLC wrapper, its learner, its presets and its reconnect rules) has been removed. The VOID engine is the only engine.
- The engine presets and the 'learn best preset' setting were removed with the old engine, because the VOID engine does not have presets.
- The separate Music, Video, Web and IPTV pages are not built yet.

## Known limits from the engine documentation
- libVLC sends only the User-Agent and Referer headers.
- Media3 does not list rtsps; those sources use VLC.
- Nothing in the engine has been compiled or run on a device yet. The first build is the real test.
