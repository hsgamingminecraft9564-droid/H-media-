# H Media Player: changelog

## 0.3.2
### Added
- Search screen: the search icon in the top corner of Home opens it. It holds the search box, the categories, and the search history. Each search can be removed, or the history cleared. Nothing is kept in Private Mode.
- Music library: Songs, Artists and Albums tabs, a Shuffle play button, artist and album under each song, the playing song highlighted, and Back to list inside an artist or album.
- Guest profile: name, age, gender and a picture, saved only on this phone. Opened from Settings and from the avatar in the Home banner.
- The app logo in the Home banner.
### Changed
- The search box and recent-search chips are removed from Home; use the search icon instead.
- The privacy text now says the profile stays on the phone.
- Version 0.3.2 (versionCode 5).
### Not yet done
- Notification with previous, next, artwork and progress (needs changes in the engine's playback service).
- Video list and player design to match the reference pictures 11 and 12.

## 0.3.1
### Changed
- Landscape video: the picture fills the screen. Controls appear on tap and hide after a few seconds while playing. The status and navigation bars hide in landscape.
- Internet search: web addresses with special characters (such as |, [, ], spaces, and accented letters) now open instead of being refused. Long addresses up to 8192 characters are allowed. Pages may open new pages by themselves.
- Back buttons show the arrow icon instead of the word "Back".
- App icon: the new H Media Player logo.
- Version 0.3.1 (versionCode 4).

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
