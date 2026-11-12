# H Media Player --- Android Responsive UI & Functional Development Specification

**Project:** H Media Player\
**Package/application ID:** `dev.hmedia.player`\
**Base:** Existing VLC for Android fork\
**Target:** Android phones and tablets, portrait and landscape\
**Design reference:** User-approved compact black/red dashboard with
Video, Music, Internet Search, IPTV and Settings.

This is an implementation specification and test plan. It is not a claim
that the source code has already been changed or that an APK has already
been built. Real compatibility depends on inspecting the current
repository, Android/Gradle versions, device APIs and existing VLC
classes.

------------------------------------------------------------------------

## 1. Product requirements

Build one Android application with five primary destinations:

1.  Video --- local video library and VLC playback.
2.  Music --- local audio library, playlists and VLC playback.
3.  Internet Search --- web, image, video and news search.
4.  IPTV --- user-provided, authorised M3U/M3U8 playlists and supported
    streams.
5.  Settings --- appearance, playback, search, browser, privacy and
    storage preferences.

The app must adapt to portrait and landscape, support different screen
sizes and font/display scaling, and keep playback functional while the
interface changes orientation.

### Non-negotiable requirements

-   Keep the existing VLC playback engine and medialibrary.
-   Do not replace functional screens with static mockups.
-   Every visible control must have a real action.
-   Do not store a paid search API key in the APK.
-   Handle loading, empty, error, permission-denied and offline states.
-   Avoid claiming that any app can search literally every page on the
    internet.
-   Keep the app responsive; network work and media scanning must not
    block the main UI thread.

## 2. Approved visual system

-   Main surfaces: near-black and charcoal.
-   Accent: red, used for selection, progress and focus.
-   Text: high-contrast white with muted secondary text.
-   Cards: compact, rounded, subtle borders and restrained glow.
-   Home: brand header, search field, five compact shortcuts, recent
    media carousel and bottom navigation.
-   Keep artwork visible but do not let it reduce text/control
    readability.

Use Android resource themes and reusable components rather than
hardcoding styles independently on every screen. Support light/dark
system settings if desired, but retain the H Media Player identity.

## 3. Responsive portrait and landscape behaviour

Use responsive layouts rather than rotating a fixed portrait screen.

### Portrait phone

-   Header and search field near the top.
-   Five compact shortcuts in a two-row or horizontally scrollable
    arrangement depending on available width.
-   Recently played items in a horizontal carousel.
-   Bottom navigation at the bottom, respecting system
    gesture/navigation insets.
-   Video player uses the available width and keeps controls reachable.
-   Library lists use one column or a compact grid.

### Landscape phone

-   Reduce vertical header height.
-   Use a two-pane layout when width permits: navigation/library at the
    side and content/player at the larger side.
-   Place video playback in the largest area; controls remain reachable
    without covering essential content.
-   The home shortcuts may become a compact side rail or a single
    horizontal row.
-   Avoid forcing portrait-only bottom navigation if it consumes
    valuable height; use a navigation rail or compact bar based on
    available width.

### Tablet / large window

-   Use a navigation rail or side navigation.
-   Use two or three content columns when space allows.
-   Keep player controls close to the media surface.
-   Do not stretch narrow cards across the entire screen.
-   Support multi-window/resizable windows where the Android project
    configuration allows it.

### Implementation approach

Use the project's existing UI toolkit. If it is XML Views, use resource
qualifiers such as `layout/`, `layout-land/`, `values-sw600dp/` and
constraint-based layouts. If it is Jetpack Compose, use window size
classes/adaptive navigation and derive the layout from available window
width, not from a hardcoded device model. Do not migrate the whole VLC
project from Views to Compose just for this redesign.

Handle configuration changes correctly. Preserve the selected media,
current playback position, queue, search query and browser state when
the device rotates. Let Android recreate activities normally unless the
existing architecture has a well-tested state-retention strategy.

## 4. Dynamic blurred artwork background --- shuffle requirement

When the user shuffles or selects a video/music item, show that
currently selected item's artwork as a background banner behind the
relevant screen.

### Required visual behaviour

-   Use the current video's thumbnail/poster or current song's album
    artwork.
-   Scale/crop it to fill the background.
-   Apply a moderate blur: the artwork should be recognisable as colour
    and shape, but details must not compete with foreground controls.
-   Add a dark translucent overlay so white text remains readable.
-   Keep the artwork behind the content, never over the playback
    controls.
-   Use a graceful fallback gradient/background if artwork is missing.
-   Update the background when the current queue item changes, including
    shuffle-next.
-   Do not change the foreground layout or cause a flash when the
    artwork changes.
-   Provide a setting to disable dynamic artwork backgrounds or reduce
    motion.

### Suggested rendering pipeline

1.  Playback engine reports the current media item.
2.  UI state observes the current media ID and artwork/thumbnail URI.
3.  Load a suitably sized image asynchronously.
4.  Downsample to the display size before blur; do not decode a
    full-resolution video frame just to blur it.
5.  Apply a moderate blur using a supported Android rendering
    method/library already compatible with the project.
6.  Add a dark scrim/overlay.
7.  Crossfade between old and new blurred images.
8.  Cancel obsolete image requests when the queue changes quickly.
9.  Release bitmap/rendering resources when the screen is no longer
    visible.

### Performance safeguards

-   Cache processed artwork by media ID and target dimensions.
-   Limit bitmap dimensions to the screen/background size.
-   Avoid continuously blurring video frames; use a static
    thumbnail/poster or album art.
-   Do not blur or reprocess the image on every animation frame.
-   Pause background transitions when app is backgrounded.
-   Respect battery saver/reduced-motion preference.
-   Test on low-memory devices for memory pressure and dropped frames.

The requested effect is a softly blurred background, not a sharp
full-screen video behind the controls.

## 5. Video section

### Library

-   Read media through the existing VLC medialibrary where possible.
-   Request only the Android media permissions needed for the supported
    OS versions.
-   Browse by folders and recent items.
-   Search filenames and metadata.
-   Sort by name, date, duration and size.
-   Offer grid/list view.
-   Show thumbnails, title, duration and useful file details.
-   Provide clear empty and permission-denied states.

### Player

-   Use the existing VLC player implementation.
-   Connect play/pause, seek, skip, volume, mute, speed, aspect ratio,
    fullscreen, audio and subtitle selection to real APIs supported by
    the current project.
-   Display actual playback time/duration and real player state.
-   Support orientation changes without restarting playback.
-   Preserve queue and position across activity recreation.
-   Use the current video's blurred thumbnail/poster as the background
    where appropriate, not a second playing video.

## 6. Music section

-   Browse songs, albums, artists, genres and folders where available.
-   Search and sort.
-   Queue, shuffle and repeat.
-   Create/manage playlists and favourites.
-   Use album art as the blurred background for the currently playing
    song.
-   Keep foreground title, artist and playback controls clear.
-   Preserve existing VLC audio service/background playback and
    notification controls where available.
-   Ensure headset/media-session controls remain synchronised with the
    UI.

When shuffle chooses a different song, update the blurred artwork from
the newly active track, not from the item that was originally tapped.

## 7. Internet Search --- Brave Search API

The user recommends Brave. Use the **Brave Search API** as the first
provider to evaluate, subject to current account eligibility, pricing,
quota, terms and endpoint availability.

Official documentation: - API documentation:
https://api-dashboard.search.brave.com/documentation/ - Web search
endpoint documentation:
https://api-dashboard.search.brave.com/app/documentation/web-search -
API reference: https://api-dashboard.search.brave.com/api-reference/

### Search categories

-   All / Web
-   Images
-   Videos
-   News

Use the corresponding documented Brave endpoints and response schemas.
Do not assume all categories return identical fields. Parse each
response according to its own schema and map it into a small internal
result model.

### Search request flow

1.  User enters a query and selects a category.
2.  Android validates basic input and sends HTTPS request to H Media
    Player backend.
3.  Backend validates category/query, enforces rate limits and reads the
    Brave API key from server-side secrets.
4.  Backend calls the official Brave endpoint.
5.  Backend normalises results into a stable app response.
6.  Android renders result cards with title, description, source/domain
    and optional image.
7.  Tapping a result opens the URL in the built-in browser.

### Backend security

-   Never place the Brave API key in Kotlin source, XML resources,
    BuildConfig distributed in the APK, or a public repository.
-   Keep the key in server-side environment variables/secrets.
-   Do not make the backend an unrestricted open proxy.
-   Validate request size, category and parameters.
-   Use HTTPS, timeouts, rate limits and safe error handling.
-   Avoid logging full sensitive queries unnecessarily.
-   Cache only where permitted by Brave's current terms.
-   Monitor quota and provide a useful quota-exceeded message.
-   Review Brave API terms, attribution requirements, retention rules
    and pricing before release.

### Search UX

-   Search field with submit and clear actions.
-   Category tabs.
-   Loading skeleton/progress.
-   Empty results state.
-   Offline, timeout, provider-error and quota-error states.
-   Paginated/load-more results only if supported by the selected
    endpoint and API plan.
-   Safe URL handling.
-   Search history with clear-history option.
-   Optional SafeSearch controls suitable for the app's audience.

### Coverage limitation

Brave Search queries Brave's maintained search index. No provider can
guarantee that every URL or page on the internet is indexed or returned.
Do not scrape search result pages or bypass provider access
restrictions.

## 8. Built-in browser

Use Android WebView for in-app website display. Build the browser chrome
yourself:

-   Address/search bar
-   Back, forward, reload/stop and home
-   Tab overview, new tab and close tab
-   Bookmarks
-   History
-   Download handling
-   Page loading progress
-   Error/offline page

### Security requirements

-   Declare `android.permission.INTERNET`.
-   Prefer HTTPS and handle cleartext traffic deliberately.
-   Do not bypass SSL certificate errors.
-   Keep JavaScript disabled unless a site/function requires it.
-   Do not expose JavaScript interfaces to arbitrary websites.
-   Prevent untrusted pages from accessing local files.
-   Handle external intent schemes through an allowlist/explicit
    confirmation.
-   Configure Safe Browsing where supported.
-   Request camera/microphone/location permissions only after a user
    action and only when needed.
-   Provide user controls to clear browsing data.
-   Release WebView instances when tabs close.
-   Test downloads and file selection without granting broad storage
    access unnecessarily.

WebView uses the device's Android System WebView/Chromium
implementation. Some sites may block embedded browsers or behave
differently from Chrome; do not promise universal compatibility.

## 9. IPTV section

-   Allow the user to add a playlist they are authorised to access.
-   Support M3U/M3U8 parsing and compatible stream types supported by
    VLC.
-   Group channels and support favourites.
-   Display EPG information when a valid compatible EPG source is
    supplied.
-   Show clear errors for unavailable, expired or unsupported streams.
-   Do not bundle unlicensed channel playlists or imply that all
    channels are free/legal.

## 10. Settings

Include: - Dynamic artwork background on/off - Blur intensity: subtle /
moderate / stronger (moderate default) - Background dim level - Reduce
motion / battery-friendly mode - Theme and accent - Default search
provider and SafeSearch - Clear browser history/cache - Media scan
settings - Subtitle/audio preferences - IPTV playlist management -
Privacy, permissions and licences

Avoid a blur slider that triggers expensive full-resolution processing
continuously. Apply changes to a cached, downsampled background.

## 11. Recommended implementation phases

### Phase 0 --- Audit and baseline

-   Inspect the actual repository and branch.
-   Identify UI toolkit, home activity, navigation, media library, VLC
    player, audio service and Gradle wrapper.
-   Build the untouched project and record baseline errors.
-   Do not upgrade dependencies before checking compatibility.

### Phase 1 --- Responsive dashboard

-   Recreate the approved home screenshot.
-   Implement portrait, landscape and tablet/adaptive layouts.
-   Connect shortcuts to real existing destinations.
-   Add bottom navigation/navigation rail appropriate to window size.

### Phase 2 --- Video and music library

-   Connect existing media data.
-   Implement browse, search, sort, recent and playlist views.
-   Confirm playback works before adding visual effects.

### Phase 3 --- Dynamic blurred artwork

-   Observe current media/queue changes.
-   Load album art or video thumbnail.
-   Downsample, blur, darken and crossfade.
-   Add caching and performance controls.
-   Test shuffle changes and rotation.

### Phase 4 --- Browser

-   Add secure WebView screen and navigation controls.
-   Test real sites and lifecycle behaviour.
-   Add tabs, bookmarks, history and downloads.

### Phase 5 --- Brave search

-   Set up a backend and server-side Brave key.
-   Implement web search first.
-   Add image, video and news categories.
-   Test rate limits, provider errors and result parsing.

### Phase 6 --- IPTV and settings

-   Add authorised user playlists and EPG.
-   Add theme, privacy, browser and background preferences.

### Phase 7 --- QA and release

-   Test portrait and landscape on physical phones.
-   Test tablet/emulator/window sizes.
-   Test low-memory conditions and background/foreground transitions.
-   Test shuffle while artwork changes rapidly.
-   Test network-offline, API quota and invalid media.
-   Verify every visible control.
-   Build release APK and inspect logs.
-   Preserve applicable VLC/GPL notices and source obligations.

## 12. QA acceptance checklist

### Responsive UI

-   [ ] Portrait layout fits without clipping.
-   [ ] Landscape layout uses available width/height efficiently.
-   [ ] Tablet layout does not stretch cards awkwardly.
-   [ ] System bars, cutouts and gesture insets are respected.
-   [ ] Larger font/display sizes remain usable.
-   [ ] Rotation preserves playback, queue, search and browser state.

### Dynamic background

-   [ ] Current video thumbnail appears behind Video UI.
-   [ ] Current song artwork appears behind Music UI.
-   [ ] Shuffle changes background to the new active item.
-   [ ] Blur is moderate and foreground text remains readable.
-   [ ] Missing artwork uses fallback.
-   [ ] Rapid queue changes do not display stale artwork.
-   [ ] Background animation pauses when not visible.
-   [ ] Memory use remains stable on lower-end devices.

### Search and browser

-   [ ] Brave web search returns results.
-   [ ] Images, videos and news categories parse correctly.
-   [ ] API key is absent from APK and public source.
-   [ ] Backend validates and rate-limits requests.
-   [ ] Offline, timeout and quota errors are handled.
-   [ ] Websites open in WebView.
-   [ ] Back/forward/reload/tabs/bookmarks/history work.
-   [ ] Unsafe URL and SSL errors are handled safely.

### Media and IPTV

-   [ ] Existing VLC video playback works.
-   [ ] Music playback and background service work.
-   [ ] Shuffle/repeat/queue work.
-   [ ] IPTV accepts user-authorised playlists.
-   [ ] Unsupported streams show useful errors.

## 13. What is required before implementation

The source repository must be inspected first. The known project path
from earlier work was:

``` bash
cd ~/H-media-player-
ls -la
cd source/vlc-android-master
```

Then inspect: - Root and app Gradle files - Existing home/navigation
activity - Existing video and audio screens - VLC playback classes and
service - Media-library models - Android manifest and permissions -
Current GitHub Actions workflow

Do not blindly replace files or assume class names. Implement against
the actual codebase and build after each phase.

## Definition of done

The app is ready only when it builds and installs, portrait and
landscape layouts both work, Video/Music still use the real VLC engine,
shuffle updates the moderately blurred current-item background,
Brave-backed search works through a secure backend, websites open in the
built-in browser, IPTV handles authorised user playlists, and every
visible control has been tested.

**No software can honestly guarantee zero problems on every Android
device.** The correct goal is a tested, recoverable implementation with
clear error handling, device coverage and no known blocking defects.
