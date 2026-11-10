# Phase 1: audit worksheet

Run `bash tools/phase1_audit.sh` from the root of your app project, then fill this in.

## Existing features (tick what works today)
- [ ] Local audio / video playback
- [ ] Pause / resume / seek / speed
- [ ] Audio and subtitle track selection
- [ ] Background playback + notification
- [ ] Gestures
- [ ] Equalizer / lyrics / IPTV / EPG / vault (whichever exist)

## VLC dependencies found
List every file and call from section [2] of the audit report. Each one moves into `VlcBackendAdapter`.

## Known bugs
- Duplicate reconnect loops:
- Pause triggers restart:
- Audio focus (calls, Bluetooth, headphones):
- Threading / UI updates:

## Safety net
- [ ] Git tag `pre-void-engine-YYYYMMDD` and branch `void-engine-migration` created (or project folder copied)
- [ ] `./gradlew assembleDebug` is green BEFORE any change
- [ ] Existing player tested on a real device
