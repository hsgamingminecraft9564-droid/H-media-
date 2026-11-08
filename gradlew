#!/bin/sh
#
# Bootstrap for Gradle. It reads gradle/wrapper/gradle-wrapper.properties, downloads the Gradle version
# named there once into ~/.gradle/wrapper/dists, checks its SHA-256 before using it, then runs it with
# the same arguments. Run it with:  sh gradlew :app:assembleDebug
#
# Running `gradle wrapper` on a computer with Gradle replaces this file with Gradle's own wrapper.

set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$PROPS" ]; then
    echo "gradlew: missing $PROPS" >&2
    exit 1
fi

# Reads one key from the properties file and undoes Gradle's escaping (\: becomes :)
read_prop() {
    sed -n "s/^$1=//p" "$PROPS" | tr -d '\r' | sed 's/\\:/:/g'
}

URL=$(read_prop distributionUrl)
EXPECTED_SHA=$(read_prop distributionSha256Sum)

if [ -z "$URL" ]; then
    echo "gradlew: distributionUrl is not set in $PROPS" >&2
    exit 1
fi
if [ -z "$EXPECTED_SHA" ]; then
    echo "gradlew: distributionSha256Sum is not set in $PROPS. Refusing to run an unverified Gradle." >&2
    exit 1
fi

NAME=$(basename "$URL" .zip)
DIST_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/$NAME"

find_gradle() {
    find "$DIST_HOME" -type f -path '*/bin/gradle' 2>/dev/null | head -n 1
}

GRADLE_BIN=$(find_gradle)
if [ -z "$GRADLE_BIN" ]; then
    mkdir -p "$DIST_HOME"
    ZIP="$DIST_HOME/$NAME.zip"
    echo "gradlew: downloading $URL" >&2
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 3 -o "$ZIP" "$URL"
    else
        wget -q -O "$ZIP" "$URL"
    fi

    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL=$(sha256sum "$ZIP" | cut -d ' ' -f 1)
    else
        ACTUAL=$(shasum -a 256 "$ZIP" | cut -d ' ' -f 1)
    fi
    if [ "$ACTUAL" != "$EXPECTED_SHA" ]; then
        rm -f "$ZIP"
        echo "gradlew: checksum does not match. The download was discarded and not used." >&2
        exit 1
    fi

    unzip -q -o "$ZIP" -d "$DIST_HOME"
    rm -f "$ZIP"
    GRADLE_BIN=$(find_gradle)
    if [ -z "$GRADLE_BIN" ]; then
        echo "gradlew: could not find bin/gradle after unpacking" >&2
        exit 1
    fi
    chmod +x "$GRADLE_BIN"
fi

exec "$GRADLE_BIN" "$@"
