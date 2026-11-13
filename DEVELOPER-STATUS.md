# H Media Player: developer status (version 0.3.1)

Legend: **Done** = in the code. **Partly** = in the code, with a known gap. **Not done** = not built.
**Not verified** = in the code, but not yet tested on a phone or in CI.

| Area | Status | Notes |
|---|---|---|
| Home screen with search, banners, thumbnails, recent items | Done | Not verified on a phone |
| Internet search (DuckDuckGo in the web viewer) | Done | Links with special characters open (0.3.1); links to other apps blocked; Private Mode clears web data |
| Recent searches hidden in Private Mode | Done | |
| Video and Music libraries from the phone | Done | Needs media permission; thumbnails need Android 10+ |
| Filter by name | Done | |
| Queue: next, previous, shuffle, repeat | Done | Not verified on a phone |
| Player controls with icons and speed | Done | Not verified on a phone |
| Landscape full-screen player with tap controls | Done | 0.3.1; not verified on a phone |
| Video fit to the video's shape | Done | Uses the engine's reported video size; not verified on a phone |
| Blurred album-art background | Done | Blur needs Android 12 or newer; older versions show a dark gradient |
| Background playback and media notification | Partly | Service declared by the VOID engine; battery setting may be needed on some phones |
| IPTV from a user-supplied M3U playlist | Done | Parser has unit tests; live streams not verified |
| Themes (four) and fonts (four) | Done | Theme engine (120 themes) not built |
| Language picker (100 languages) | Done | Translations: eight languages, need native review |
| Translated text in eight languages | Partly | 92 languages show English |
| Privacy policy text | Partly | Only English |
| Icons and launcher icon | Done | Launcher icon is the H Media Player logo (0.3.1); not verified on a phone |
| Search screen with history (top-corner search) | Not done | Planned for the next batch |
| Notification with previous, next, artwork and progress | Not done | Needs changes in the engine's playback service |
| Music list with Songs/Videos/Artists/Albums tabs | Not done | Planned for the next batch |
| Guest profile (name, age, gender, photo) | Not done | Waiting for field details |
| Unit tests | Done | Search URL, search parsing, web policy, M3U parser, engine tests |
| Android device tests | Partly | One welcome-gate test; the device test plan has 35 manual cases |
| Build in GitHub Actions | Not verified | Version 0.3.0 has not been built yet |
| Release signing | Not done | Debug build only |
| Theme engine (120 themes, previews, Apply) | Not done | Specified in the theme engine summary; Phase 1 audit done |
