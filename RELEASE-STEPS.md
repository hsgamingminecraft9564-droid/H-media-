# Publish the app as a direct download

GitHub's build results (Actions artifacts) always come as a zip file. To give people the app itself,
publish a release: a version tag makes the build attach the APK to the Releases page.

## Release a new version

1. Commit and push the code as usual.
2. Create and push a tag with the version number:

       git tag v0.5.1
       git push origin v0.5.1

3. Wait for the Build APK run to finish green on the Actions tab.
4. The APK appears on the Releases page as H-Media-Player.apk.

## The download link

    https://github.com/hsgamingminecraft9564-droid/H-media-/releases/latest/download/H-Media-Player.apk

This link always opens the newest release. Opening it on a phone downloads the app file directly.

## Notes

- The release uses the debug build. It installs and works, but a public release should be signed with
  your own release key before it is shared widely.
- Android asks the person to allow installs from the browser the first time. This is normal for apps
  that are not from the Play Store.
- If the build fails, no release is made. Check the build log on the Actions tab.
