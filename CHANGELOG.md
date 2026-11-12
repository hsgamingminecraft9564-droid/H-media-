# H Media Player: changelog

## 0.3.0
### Added
- Home screen: banner header, internet search bar with All, Images, Videos and News options,
  recent searches (hidden in Private Mode), banner cards with thumbnails of your videos and music.
- Video and audio thumbnails in the home cards and the library lists (Android 10 and newer).
- Icons: 53 glass-style icons on the bottom bar, the side rail, the home shortcuts and the player
  controls, and a new launcher icon.
- Video fit: the picture takes the shape of the video, so the whole frame shows.
- Player: previous, play/pause, next, shuffle and repeat with icons and labels; speed 1x to 2x.
- Library screens re-check media access and reload files when you return to the app.
- Language: the settings list shows 100 languages. Eight are translated: Spanish, French,
  German, Portuguese, Hindi, Nepali, Russian and Arabic (right to left).
- IPTV: playlists you supply, parsed by a tested M3U reader.
- Version 0.3.0 (versionCode 3).

### Changed
- Internet search opens DuckDuckGo pages in the app's own web viewer.
- The settings language list uses the full 100-language list instead of 21 choices.

### Fixed during development
- Icon file names with hyphens are renamed (Android rejects hyphens in resource names).
- Missing imports and a misnamed Compose function were found by review and fixed before packaging.

### Removed
- The learner, engine presets, the smart engine switch, and the "learn best preset" setting.
  The VOID engine replaces the old engine and has no presets.
- The search results screen is no longer reachable. The search server remains in the repository.

## Earlier versions
Versions 0.1.0 and 0.2.0 are in the git history. Their changelog described the old engine,
which is now removed.
