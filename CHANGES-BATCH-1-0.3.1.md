# Batch 1 (version 0.3.1): what changed and what is still to do

## Done in this batch (not yet compiled or run on a phone)
1. Landscape video: full-screen picture, tap to show controls, auto-hide while playing, system bars hidden.
2. Internet search links: special characters are encoded instead of refusing the link. Length limit 8192. JavaScript may open pages; the link check still decides where each page goes. Tests updated.
3. Back buttons: arrow icon.
4. App icon: the H Media Player logo at all five densities; the adaptive glass icon was removed.

## Still to do (next batches)
- Notification and lock screen: prev, next, play/pause, artwork and progress. This is in the engine's playback service; the session must expose the queue and artwork.
- Home search: a search icon in the top corner opens a search screen with recent searches and history. The big search box and the recent chips on Home are removed.
- Music list: Songs / Videos / Artists / Albums tabs, Shuffle play row, sort, artist and album under each title, now-playing marker, and a mini player like the reference.
- Video list and player design to match the reference pictures (thumbnails, progress, overlay controls).
- Guest profile: name, age, gender and a profile picture, stored only on the phone (Photo Picker, no permission needed). The privacy text must be updated to list these fields.
- In-app logo on the Home header (the launcher icon is done).

## How to check batch 1 on your phone
- Rotate the phone to landscape with a video: the picture should fill the screen; tap to show controls.
- Open a search result, then open a link inside the page that contains special characters or a long address.
- Check the back arrow on the player and in Settings.
- Check the app icon on the home screen after installing version 0.3.1 (uninstall the old version first only if the install is refused).
