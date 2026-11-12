# H Media Player --- Android Development Roadmap

**Project goal:** Build a working H Media Player Android APK using the
existing GitHub project, an Android phone, and Termux.

**Approach:** Work in small, verifiable phases. Preserve the existing
VLC playback engine and project identity. Do not replace working project
components without inspecting them first.

------------------------------------------------------------------------

## Phase 1 --- Inspect and prepare the project

-   [ ] Open the existing repository in Termux.
-   [ ] Check the Git branch, working tree, remotes, and source folders.
-   [ ] Inspect Gradle wrapper files and project build configuration.
-   [ ] Check Java, Gradle, Android SDK, and NDK availability.
-   [ ] Identify the current app entry point, navigation, playback, and
    media-library code.
-   [ ] Create a clean Git checkpoint before major edits.

**Completion check:** The project structure and build requirements are
understood, and the current source is safely backed up in Git.

## Phase 2 --- Redesign the home screen

-   [ ] Create a compact dashboard with a black/charcoal base and red
    accents.
-   [ ] Add a search bar.
-   [ ] Add shortcuts for Video, Music, Internet Search, IPTV, and
    Settings.
-   [ ] Show recently played media with thumbnails.
-   [ ] Add clear navigation to Home, History, Library, Playlist, and
    Now Playing where appropriate.

**Completion check:** The home screen builds and navigation works
without breaking existing playback.

## Phase 3 --- Integrate Video and Music

-   [ ] Inspect and reuse the existing VLC playback integration.
-   [ ] Connect the video and music library screens to real media data.
-   [ ] Add playback controls, queue management, shuffle, and repeat.
-   [ ] Add history and playlist support.
-   [ ] Add a dynamic blurred background using the current video's
    thumbnail or current track's album art.
-   [ ] Keep foreground controls readable with a dark overlay.
-   [ ] Cache appropriately sized artwork and provide a fallback when
    artwork is unavailable.

**Completion check:** Local video and music files play reliably, and the
artwork background updates when the current queue item changes.

## Phase 4 --- Add Internet Search and an in-app browser

-   [ ] Start with provider search pages (for example, Google, Bing, and
    DuckDuckGo) inside Android WebView.
-   [ ] Add provider selection in Settings.
-   [ ] Add All, Images, Videos, and News search categories where
    supported by the selected provider.
-   [ ] Add browser back, forward, refresh, and home controls.
-   [ ] Add tabs, bookmarks, browsing history, and downloads in stages.
-   [ ] Use HTTPS, handle navigation safely, and never bypass
    certificate errors.
-   [ ] Do not scrape search results or embed private API keys in the
    APK.

**Cost note:** Provider search pages are a free-to-start approach, but
provider behavior and availability inside WebView can vary. This does
not create custom native search-result cards. A later API-based search
system may require a paid provider or a separately maintained backend.

**Completion check:** Searches open successfully in the in-app browser
and basic browser navigation works.

## Phase 5 --- Add IPTV

-   [ ] Allow users to add their own authorized M3U/M3U8 playlist.
-   [ ] Parse and display channel names and stream URLs.
-   [ ] Play supported streams using the existing playback engine.
-   [ ] Add useful loading, empty, and playback-error states.
-   [ ] Add EPG only when the user supplies a compatible EPG source.

**Completion check:** A user-provided playlist loads and supported
channels play.

## Phase 6 --- Settings and responsive layouts

-   [ ] Add settings for theme, artwork background, and animation.
-   [ ] Provide portrait and landscape layouts.
-   [ ] Keep controls compact and accessible on smaller screens.
-   [ ] Add reduced-animation options to help limit battery and memory
    use.
-   [ ] Check text contrast and touch-target sizes.

**Completion check:** Main screens remain usable in both orientations
and settings take effect.

## Phase 7 --- Test and fix

-   [ ] Build after each major feature rather than waiting until the
    end.
-   [ ] Test local video and music playback.
-   [ ] Test shuffle, repeat, queue changes, and artwork updates.
-   [ ] Test search providers and browser navigation.
-   [ ] Test IPTV with a playlist the user is authorized to use.
-   [ ] Check app behavior after rotation, backgrounding, and reopening.
-   [ ] Fix crashes, broken navigation, and layout issues.

**Completion check:** The app passes the feature checks on the target
Android device.

## Phase 8 --- Build and release the APK

-   [ ] Confirm the GitHub Actions workflow and Android build
    configuration.
-   [ ] Build a debug APK first.
-   [ ] Install and verify the APK on the phone.
-   [ ] Resolve build or runtime errors before release.
-   [ ] Configure release signing securely; do not commit keystore
    passwords or private signing files.
-   [ ] Publish the source and required notices for the VLC-derived GPL
    project.
-   [ ] Create a GitHub release with the verified APK and release notes.

**Completion check:** A tested APK is available from the GitHub release.

------------------------------------------------------------------------

## Working rules

1.  Complete one phase at a time.
2.  Run commands from the correct project directory.
3.  Inspect command output before proceeding.
4.  Make a Git commit or backup before substantial changes.
5.  Never claim a feature is complete until it has been built and
    tested.
6.  Preserve VLC and medialibrary functionality unless inspection proves
    a change is necessary.
7.  Keep API keys, signing credentials, and passwords out of the
    repository.

## Current status

**Roadmap status:** Planning only. No source-code changes or APK build
are implied by this document.

**Next action:** Inspect the current Termux project directory and build
environment, then complete Phase 1.
