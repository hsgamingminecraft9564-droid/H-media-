# H Media Player: icons, video thumbnails, video fit, languages

## Added
- Icons: 53 Glassmorphism icons (Android vector drawables) in res/drawable. Five names that
  contained hyphens were renamed with underscores (Android rejects hyphens in resource names).
- Launcher icon: adaptive icon (glass tile) from the Glassmorphism pack.
- Video and audio thumbnails on home cards and library rows (Android 10 and newer). The red
  banner is shown when there is no thumbnail.
- Video fit: the picture takes the video's own shape (from the engine), with black bars where
  needed, so the whole frame is visible in portrait and landscape.
- Languages: the 100-language picker (AppCompat per-app language, Android 13+ system setting
  supported). Translated: Spanish, French, German, Portuguese, Hindi, Nepali, Russian, Arabic.

## Still to do
- Translations for the other 92 languages. Missing text falls back to English.
- Native-speaker review of the eight translations.
- Privacy policy body text (privacy_text) is English in every language.
- Universal Icon Themes (7 themes x 374 icons) are not wired yet; a theme picker can be added later.
- Device testing: this build has not been compiled in this environment.
