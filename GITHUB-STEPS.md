# H Media Player: GitHub to APK in 5 steps

1. **Create the repository.** On github.com click New, name it `h-media-player`, leave it empty, and create it.
2. **Upload the files.** Unzip this package. Open the new repository, click "Add file" then "Upload files",
   drag in everything inside the unzipped folder (not the folder itself), and click "Commit changes".
   Or with git: `git init && git add . && git commit -m "H Media Player" && git branch -M main`
   then `git remote add origin <your-repo-url>` and `git push -u origin main`.
3. **Wait for the build.** Open the Actions tab. "Build APK" starts on its own after the upload.
   Green means success. To start it again, pick "Build APK", then "Run workflow".
4. **Download the APK.** Open the green run, scroll to Artifacts, and download
   `H-Media-Player-debug`. Unzip it to get `app-debug.apk`.
5. **Install on the phone.** Copy the APK to the phone, allow "Install unknown apps" for your file manager,
   and open the file. Allow notifications when the app asks.

## Building on your own computer
Run `sh gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`). The script downloads
Gradle 8.11.1 the first time, checks its checksum, and then builds. CI does not need it.

Optional: on a computer with Gradle installed, run `gradle wrapper --gradle-version 8.11.1` once and commit
the generated files. They replace the bootstrap script with Gradle's own wrapper.

## Device tests
Run the emulator tests from the Actions tab: "Device tests (emulator)", then "Run workflow".
Real-phone testing follows DEVICE-TEST-PLAN.md, which lists 35 cases with steps and expected results.

## Internet search
The internet search opens DuckDuckGo pages inside the app. It needs no server and no setup.
The `search-server/` folder is kept for a future search service and is not used by the current screens.

## Background music on your phone
For music to keep playing with the screen off, set H Media Player's battery usage to "No restrictions"
(Settings, Apps, H Media Player, Battery). Phones such as Xiaomi and Redmi stop background apps
without this.

## If the build turns red
Open the failed run, click the red step, and copy the lines that start with `e:` or `FAILURE`.
Send them to me with the words "build error". Those lines show exactly what to fix.

## Notes
- The APK is a debug build, signed with a debug key. It installs fine for testing; a Play Store release needs a signing key.
- libVLC is licensed under LGPL 2.1. Keep its license notice if you share the APK.
