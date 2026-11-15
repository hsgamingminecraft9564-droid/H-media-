# Signing setup: updates install over the old version

Android installs a new version over an old one only when both are signed with the same key.
Every published build must use the same key, so set it up once and keep it safe.

## 1. Create the key (once, in Termux)

    keytool -genkeypair -v -keystore ~/hmp-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hmp-upload

Choose a strong password and write it down somewhere safe. Keep the file ~/hmp-upload.jks too.
If you lose the key, you cannot publish updates that existing users can install.
Never commit the key file to GitHub.

## 2. Add four secrets on GitHub

Repo Settings, then Secrets and variables, then Actions, then New repository secret:

- HMP_KEYSTORE_B64: the key file in base64 (see below)
- HMP_STORE_PASSWORD: the keystore password
- HMP_KEY_ALIAS: hmp-upload
- HMP_KEY_PASSWORD: the key password (the same as the keystore password if you pressed Enter at the prompt)

To get the base64 text:

    base64 -w 0 ~/hmp-upload.jks > ~/hmp-upload.b64

Open ~/hmp-upload.b64 and copy the whole line into the HMP_KEYSTORE_B64 secret.

## 3. Publish

Push the code, then push a version tag (for example v0.5.3). The build signs the APK with your key.

## Phones with an older build

Builds made before this setup were signed with different keys, so those phones need one uninstall.
Uninstalling removes the app's settings and lists. After that, every new version installs as an update.
