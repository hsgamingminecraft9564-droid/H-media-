# H Media Player — Theme Engine Implementation Summary

**Purpose:** Technical hand-off for implementing a reliable, maintainable theme system in H Media Player.

**Status:** This is an implementation specification, not proof that the feature has been integrated or compiled. The complete Android source project must be inspected before code changes.

## 1. Product goal

Create an in-app Theme Gallery where users can browse themes, see an accurate preview, preview the real app screens, and apply a theme. The selected theme must remain active after app restarts. The gallery and preview thumbnails should work offline.

## 2. Theme count

Initial target: **120 themes in 12 collections**, 10 themes per collection.

| Collection | Count |
|---|---:|
| Modern Minimal | 10 |
| Minimal Light | 10 |
| Anime & Japan | 10 |
| Gaming & Pixel | 10 |
| Neon & Cyber | 10 |
| Nature & Landscape | 10 |
| Luxury & Royal | 10 |
| Glass & Gradient | 10 |
| Retro & Arcade | 10 |
| AMOLED & Monochrome | 10 |
| Material & Pastel | 10 |
| Seasonal & Artistic | 10 |
| **Total** | **120** |

These are original design concepts, not copies of other apps. Use original artwork or properly licensed assets; record the source and licence for every third-party image. 120 themes means 120 configurations, not 120 separately coded interfaces.

## 3. User flow

1. Open **Settings → Themes**.
2. Browse collections or search by theme name.
3. Each card shows a thumbnail, theme name, collection, and colour swatches.
4. Tap **Preview** to see the theme on Home, Music Player, and Video Player examples.
5. Tap **Apply** to save and use the theme.
6. The app updates all themed screens and restores the selected theme after restart.
7. Also provide **System Default**, **Light**, and **Dark** modes.

Opening a preview must not change the saved theme. Only Apply should persist the selection.

## 4. How the engine works

```text
Theme Gallery
   |
   v
Theme card (image + name + collection)
   |
   +----> Preview (temporary; not saved)
   |          |
   |          v
   |     Actual Home / Music / Video UI components
   |
   v
Apply theme
   |
   v
Theme Manager validates ID and colour tokens
   |
   v
Save selected theme ID
   |
   v
Shared theme state updates
   |
   v
All supported screens render with the same tokens
```

## 5. Theme data model

Each theme should have:
- Stable unique ID (do not use display name as the ID).
- Display name and collection.
- Light/dark classification.
- Semantic colour tokens.
- Optional supported shape settings.
- Preview image resource name.
- Attribution/licence metadata when third-party assets are used.

Recommended semantic tokens:

| Token | Purpose |
|---|---|
| `background` | Main screen background |
| `surface` | Cards, panels, sheets |
| `surfaceVariant` | Secondary containers |
| `primary` | Main accent and selected controls |
| `onPrimary` | Text/icons on primary |
| `textPrimary` | Main text |
| `textSecondary` | Supporting text |
| `divider` | Separators and outlines |
| `playerControls` | Playback control accents |
| `progressTrack` | Unplayed progress track |
| `progressActive` | Played progress portion |
| `error` | Error states |
| `success` | Success states |

Use safe defaults for missing tokens so malformed or older theme definitions cannot crash the app.

## 6. Preview images and live preview

**Gallery thumbnail:** Bundle one compact PNG/WebP preview per theme. It should show a representative player UI. Label illustrative mockups as previews; do not imply they are screenshots of the running app.

**Live preview:** Render actual app components using temporary theme state. Include Home, Music Player, and Video Player. Previewing must not start playback, downloads, or background work. The actual app UI is the source of truth; a static picture alone does not prove the theme works.

## 7. Architecture requirements

Before coding, inspect:
- Gradle wrapper, Android Gradle Plugin, Kotlin/Java versions.
- Minimum/target SDK and application ID.
- App modules and included modules.
- Whether the app uses XML Views, Fragments, custom Views, Compose, or a mixture.
- Existing styles, themes, colours, night-mode resources, and preferences.
- VLC player UI and custom controls.
- Existing dependencies and compatible AndroidX versions.

Implement the Theme Manager to fit the project's actual architecture. Do not assume Compose or add AppCompat/Material libraries until compatibility is checked.

The manager should provide equivalent operations to:
- `getAvailableThemes()`
- `getThemeById(id)`
- `getSelectedThemeId()`
- `previewTheme(id)` — temporary only
- `applyTheme(id)` — validate, persist, notify UI
- `resetToSystemTheme()`

Persist a stable theme ID using the project's existing preference system if suitable. Do not persist a full theme object.

## 8. Important cautions

1. Do not patch the debug APK. Integrate into Android source.
2. Do not overwrite existing `strings.xml`, styles, themes, or manifest files; merge carefully.
3. Do not assume the project uses Compose.
4. Avoid upgrading Gradle, Kotlin, Android Gradle Plugin, SDK, or NDK unless required and separately validated.
5. Do not replace VLC playback logic just to change appearance. Theme existing controls.
6. Do not hard-code unrelated colours in individual screens; use shared semantic tokens.
7. Never use low-contrast text or icons. Validate every theme and state.
8. Bundle gallery thumbnails so the gallery works offline.
9. Compress thumbnails and measure the APK-size increase.
10. Do not redistribute unlicensed internet artwork, logos, or copied themes.
11. Do not block app startup while loading the gallery.
12. Handle missing images, invalid colour values, and unknown theme IDs with safe fallbacks.
13. Do not claim all 120 themes work until all definitions pass validation and the app is tested.
14. Respect system dark mode, font scaling, and accessibility settings.
15. Keep the change set focused; do not modify unrelated player features.

## 9. Quality checks

### Build checks
- Clean debug build succeeds.
- Debug APK assembles and installs.
- Release build assembles when valid signing configuration is available.
- No missing resources, duplicate resource names, manifest merge errors, or dependency conflicts.
- No new theme-related lint errors.

### Theme data checks
- All 120 IDs are unique.
- All 120 preview paths exist.
- Every theme has required tokens or receives safe defaults.
- Colour values parse correctly.
- Contrast checks run for every theme.
- Invalid or unknown theme IDs fall back safely.

### Functional checks
- Gallery, collection filters, and search work.
- Preview does not alter the saved selection.
- Apply updates all intended screens.
- Selection survives restart and process recreation.
- System Default, Light, and Dark modes work.
- Theme switching while media is playing does not stop playback or reset the queue/position.
- Back navigation from preview returns to the gallery correctly.

### Visual and accessibility checks
- Check text and icon contrast.
- Check enabled, disabled, pressed, focused, and selected states.
- Check progress bars, sliders, dialogs, menus, and sheets.
- Check long titles and translated text for clipping/overlap.
- Check large font/display scaling.
- Check portrait and landscape.
- Check both light and dark system settings.
- Test on the oldest supported Android version and a current Android version.

### Regression checks
Verify audio/video playback, subtitles, audio-track selection, playlists, file browsing, permissions, background playback, notifications, and media-session controls. Theme changes must not reset playback position, queue, or user preferences.

## 10. Performance and APK size

- Keep theme metadata compact.
- Use WebP for artwork where appropriate; PNG can suit sharp UI mockups.
- Size thumbnails for actual display dimensions plus a modest density margin.
- Do not load all full-resolution images into memory at once.
- Reuse the project's existing image-loading/caching library if possible.
- Measure APK size before and after adding previews.
- Test smooth scrolling on lower-memory devices.
- Do not require network access to browse bundled themes.

## 11. Suggested implementation phases

**Phase 1 — Project audit:** Inspect the complete source, build files, manifest, UI framework, existing theme resources, and preferences. Record the current successful build state.

**Phase 2 — Theme engine:** Add the data model, validation, defaults, manager, persistence, and system theme modes.

**Phase 3 — Gallery:** Add collections, search, cards, bundled thumbnails, live preview, Apply, and optionally Favorites.

**Phase 4 — App-wide integration:** Connect Home, music, video, settings, dialogs, and other supported UI surfaces to shared tokens.

**Phase 5 — Automated validation:** Check all 120 definitions, IDs, image paths, required tokens, contrast, and fallback behaviour.

**Phase 6 — Regression and APK:** Build debug APK, install and test playback, inspect logs, fix errors, and produce a release build only when signing is correctly configured.

## 12. Definition of done

The feature is complete only when:
- All 120 themes appear with visual thumbnails.
- Users can preview before applying.
- Live previews use actual app UI components.
- Applying a theme updates all intended screens consistently.
- Selection persists across restarts.
- System, Light, and Dark modes work.
- All definitions and preview paths pass automated validation.
- Contrast/accessibility checks pass.
- Existing playback features pass regression tests.
- Debug APK builds and installs successfully.
- Tested source changes and build notes are delivered together.

## 13. Current project status and next requirement

The previously uploaded `H media player.zip` was a partial source bundle and did not contain all required Gradle, manifest, module, and dependency files. Therefore, this document is a specification; it does not claim the Theme Engine is integrated or build-tested.

**Required before coding:** provide the complete project source ZIP, including root Gradle files, app module, all referenced modules, Android manifest, and existing resources. Exclude generated build folders and signing keys. Preserve the existing project structure.

First audit the complete project and confirm its current build and UI framework. Then implement the theme system in small, testable changes.
