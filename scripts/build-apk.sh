#!/usr/bin/env bash
# Builds a sideload-ready Cosmic APK.
#
# Both debug and release variants in this project are signed with Android's
# debug keystore (see app/build.gradle.kts). That's fine for sideloading to
# your own / a friend's phone — it just means the package isn't Play Store
# distributable. The release variant is preferred because it's R8-shrunk
# and has the canonical applicationId (no .debug suffix).
#
# Usage:
#   ./scripts/build-apk.sh                # release APK (default)
#   ./scripts/build-apk.sh debug          # debug APK
#   ./scripts/build-apk.sh release clean  # clean build then release
#
# Output: copies the resulting APK to ./out/cosmic-<version>-<variant>.apk
# and prints the path.

set -euo pipefail

VARIANT="${1:-release}"
EXTRA="${2:-}"

case "$VARIANT" in
    debug|release) ;;
    *)
        echo "error: variant must be 'debug' or 'release', got '$VARIANT'" >&2
        exit 2
        ;;
esac

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

# Aegis (the user's loopback firewall) blocks Gradle's daemon-to-daemon
# localhost traffic. If it's running we stop it for the duration of the
# build and restart it after — same trick scripts/build-youtubedl-aar.sh
# uses. Skip silently if it isn't installed.
AEGIS_WAS_RUNNING=0
if systemctl is-active --quiet aegis.service 2>/dev/null; then
    echo "→ stopping aegis.service for build duration"
    sudo systemctl stop aegis.service
    AEGIS_WAS_RUNNING=1
fi
restore_aegis() {
    if [ "$AEGIS_WAS_RUNNING" = "1" ]; then
        echo "→ restarting aegis.service"
        sudo systemctl start aegis.service || true
    fi
}
trap restore_aegis EXIT

if [ "$EXTRA" = "clean" ]; then
    echo "→ ./gradlew clean"
    ./gradlew clean
fi

GRADLE_TASK="assemble$(tr '[:lower:]' '[:upper:]' <<< "${VARIANT:0:1}")${VARIANT:1}"
echo "→ ./gradlew :app:$GRADLE_TASK"
./gradlew ":app:$GRADLE_TASK"

# Resolve the produced APK. AGP writes it under app/build/outputs/apk/<variant>/.
SRC_APK="$(find "$ROOT/app/build/outputs/apk/$VARIANT" -maxdepth 1 -name '*.apk' -print -quit)"
if [ -z "$SRC_APK" ] || [ ! -f "$SRC_APK" ]; then
    echo "error: APK not found under app/build/outputs/apk/$VARIANT" >&2
    exit 1
fi

# Pull the version name from the gradle file so the output filename stays
# in sync as we bump versions.
VERSION="$(grep -E '^\s*versionName\s*=' app/build.gradle.kts \
    | head -n1 | sed -E 's/.*"(.+)".*/\1/')"
[ -z "$VERSION" ] && VERSION="unversioned"

mkdir -p "$ROOT/out"
DEST="$ROOT/out/cosmic-$VERSION-$VARIANT.apk"
cp -f "$SRC_APK" "$DEST"

SIZE_MB="$(du -m "$DEST" | cut -f1)"
echo
echo "✓ APK built: $DEST  (${SIZE_MB} MB)"
echo
echo "To install on a phone over USB:  adb install -r '$DEST'"
echo "To send via Telegram/Signal/etc, just attach that file."
echo "Recipient must enable 'Install from unknown sources' for their"
echo "messenger app the first time they install a sideloaded APK."
