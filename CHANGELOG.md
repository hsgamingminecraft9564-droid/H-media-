# H Media Player: changelog

## 0.4.0
### Added
- Custom background: Settings, Background, Choose from gallery. The picture is copied into the app, scaled down and dimmed. Default background restores the original look. No storage permission is needed.
### Not verified
- Not compiled or tested on a phone yet.

## 0.3.9
### Changed
- Internet search opens Google results in the app's own web viewer. Images, Videos and News use Google's own tabs.
### Notes
- Google may show a verification screen to embedded browsers. If that happens, showing results inside the app needs a Google search API key (decision pending).
### Not verified
- Not compiled or tested on a phone yet.

## 0.3.8
### Changed
- While a video plays, its frame is shown blurred behind the app.
- Video mini bar: film icon, title, status, a play or pause button and the three dots (opens the player).
- Bottom bar: wider and darker, thin dividers, the selected item with a red glow.
### Not verified
- Not compiled or tested on a phone yet.

## 0.3.7
### Changed
- Music and Video screens redrawn in the reference style: top bar with logo and name, search and profile, screen title with red bar, pill tabs, red shuffle button, rounded filter, rows with the three dots, length and file size.
- Bottom bar: floating rounded bar, selected item in red.
- Mini player: rounded bar with picture, title, previous, play or pause and next.
### Not done yet
- Settings and IPTV screens are not yet redrawn to the reference.
- Exact pixel match is not guaranteed: the font is the app's own font, and the layout is matched by eye.
### Not verified
- Not compiled or tested on a phone yet.

## 0.3.6
### Fixed
- Portrait video: the picture fills its area and is fitted inside it, so it is no longer squeezed into a narrow column.
- Portrait video: the controls (back, title, previous, play, next, progress) stay visible instead of hiding after a few seconds. Landscape still hides them while playing.
- App icon: FILES-TO-DELETE.txt lists the old icon files that must be removed from the repository with git, so the new logo shows.
### Not verified
- Not compiled or tested on a phone yet.

## 0.3.5
### Added
- IPTV: search box for channels, All and Favourites switch, a star to add or remove a favourite, and a generated thumbnail for each channel (a colour tile with the channel's initials).
### Changed
- The screen stays on while a video or channel is playing (music may still play with the screen off).
- Profile picture: a square copy is kept inside the app, so it stays after a restart.
- Home: the logo and name are small at the top left, the banner is shorter, and the Video, Music, IPTV and Settings chips are removed (use the bottom bar).
### Not done yet
- Background picture setting (built-in pictures and your own).
- Music and video screens redrawn 1:1 from the reference pictures, and the modern look.
- App icon: the old icon in the screenshot means the newest build is not installed. Uninstall, then install version 0.3.5.
### Not verified
- Not compiled or tested on a phone yet.

## 0.3.4
### Changed
- Video player in portrait: the picture is at the top with its controls on it (play, previous, next, progress, back), and the title, status, shuffle, repeat and speed are below it.
- Video list: each video shows its length on the thumbnail.
### Not verified
- Not compiled or tested on a phone yet.

## 0.3.3
### Added
- Notification and lock screen: previous and next buttons, the track's title, artist and album, and its thumbnail as album art. Headset and Bluetooth next and previous work too.
- Engine: NowPlayingBridge (what the notification shows and the next and previous actions), and VoidMediaSessionPlayer (adds next and previous to the media session).
### Not verified
- The notification has not been compiled or tested on a phone yet. Check the buttons, the artwork and the progress bar on Android 13 and newer.
- Video list and player design to match reference pictures 11 and 12 is still to do.

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
