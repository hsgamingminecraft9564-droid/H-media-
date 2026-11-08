# H Media Player: device test plan

Run these on a real phone before any release. Record the result in the last column.
Use PASS, FAIL (with a short note), or SKIP (with the reason, for example "no Bluetooth headphones").

## Test record
- Phone model:
- Android version:
- App version (Settings, or the APK name):
- Date:
- Tester:
- Other apps running during the test:

## Setup
1. Install the APK from the latest green GitHub build.
2. Allow notifications when asked.
3. Set battery usage for H Media Player to "No restrictions".
4. Have ready: one short audio file, one video file (under 2 minutes), one web stream address you can use, and a pair of wired or Bluetooth headphones.

## Cases

| ID | Area | Steps | Expected | Result |
|---|---|---|---|---|
| D01 | Install | Install the APK, open the app. | Opens to the welcome screen with the castle picture. The H logo is the launcher icon. | |
| D02 | Welcome | Tap "Continue as Guest" before ticking the consent box. | Button is greyed out and does nothing. | |
| D03 | Welcome | Tick the consent box, then read the privacy policy. | Policy text opens and closes. Guest and Private Mode now work. | |
| D04 | Permissions | On Home, type a word in the search box. | A reason and "Allow media access" appear. No dialog yet. | |
| D05 | Permissions | Tap "Allow media access", then Allow. | Results from this phone appear after the typing pause. | |
| D06 | Permissions | Deny media access twice. | After the second denial, "Open app settings" appears and opens the app's settings page. | |
| D07 | Search | Search for a word from a file on the phone. | Matching audio and video are listed, labelled Audio or Video. | |
| D08 | Search | Search for a word with no matches. | "No results for ..." is shown. | |
| D09 | Search | Turn off mobile data and Wi-Fi, search the web. | A plain "Can't reach search right now" message, no crash. | |
| D10 | Search | Turn on search, search the web, tap a result. | The page opens inside the app. Other pages open only as web pages. | |
| D11 | Web | Open a page that tries to open an app link (for example a market or intent link). | The link is blocked. The app does not switch to another app. | |
| D12 | Web | Turn off "Scripts on web pages" in Settings, open a result page. | The page loads without scripts. Turn the setting back on. | |
| D13 | Private Mode | Turn on Private Mode. Open the search box. | No history suggestions. The note "Private Mode is on..." is shown. | |
| D14 | Private Mode | Search in Private Mode, restart the app, turn Private Mode off. | The private search does not appear in suggestions. | |
| D15 | Audio | Play the audio file. | Sound plays. The progress bar moves. Time is shown. | |
| D16 | Audio | Drag the progress bar, then press Pause and Play. | Playback jumps and resumes at the new place. Pause stops sound at once. | |
| D17 | Video | Play the video file, rotate the phone. | Picture fills the screen. Playback continues from the same place, not from the start. | |
| D18 | Video | Let the video finish. | "Finished. Press Play to play again." is shown. Play starts it from the beginning. | |
| D19 | Background | Play music, turn off the screen. Wait 2 minutes. | Music keeps playing. | |
| D20 | Background | Play music, open a game or another app. | Music keeps playing. The notification shows the title. | |
| D21 | Notification | Use Pause and Play in the notification. | Each button works. The button label changes. | |
| D22 | Notification | Use Stop in the notification. | Music stops, the notification goes away, the app returns to Home if it was open. | |
| D23 | Lock screen | Play music, lock the phone. | Media controls appear on the lock screen and work. | |
| D24 | Calls | Play music, receive a phone call (or call the phone from another phone). | Music pauses during the call and does not restart by itself after it. | |
| D25 | Headphones | Play music on headphones, unplug them. | Music pauses. Plugging them back in does not start it by itself. | |
| D26 | Bluetooth | Play music on Bluetooth headphones, switch them off. | Music pauses. Switching them back on does not restart it by itself. | |
| D27 | Video background | Play a video, press the home button. | Video pauses. Coming back and pressing Play resumes the picture. | |
| D28 | Stream | Play a web stream address. Turn Wi-Fi off for 5 seconds, then on. | The app reconnects and resumes without a crash. A retry does not restart after a manual Pause. | |
| D29 | Stream | Play a web stream, press Pause, then turn Wi-Fi off and on. | Nothing restarts by itself while paused. | |
| D30 | Stream | Play a web stream with no network for a long time. | After several tries the app shows "Connection lost. Tap Play to try again." Play tries again. | |
| D31 | Unsupported | Open a file whose address the app cannot handle, if one can be found. | A message says it cannot be played. The app does not crash. | |
| D32 | Settings | Change theme, font and language. Restart the app. | The choices are kept. Language text changes where translated; other text stays English. | |
| D33 | Rotation | Rotate while on Settings and on Home. | Layout adjusts, nothing is lost. | |
| D34 | Battery | Play music for 30 minutes with the screen off. | Music keeps going. Note any battery use that seems high. | |
| D35 | Memory | Play music, open many other apps, then return. | The app still shows the playing song (or the strip on Home shows it). | |

## Summary
- Passed:
- Failed (IDs and short notes):
- Skipped (IDs and reasons):

Send the summary and any FAIL notes with the phone model and Android version.
