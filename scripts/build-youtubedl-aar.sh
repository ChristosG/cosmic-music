#!/usr/bin/env bash
#
# Rebuilds the youtubedl-android AAR from upstream and drops it into
# core/extractor/libs/.
#
# Why we maintain a local AAR instead of pulling from jitpack:
# upstream's 0.16+ tags require JDK 17 to build, and jitpack's auto-builders
# don't have JDK 17, so anything newer than 0.15.0 (which ships Python 3.8)
# fails to resolve via Gradle. Modern yt-dlp needs Python 3.10+, so we have
# to build a current wrapper ourselves.
#
# Usage: ./scripts/build-youtubedl-aar.sh [tag]
# Default tag: 0.18.1
#
# Requirements: JDK 17 or 21 on PATH, ANDROID_HOME pointing at an SDK with
# build-tools and platforms 34+.

set -euo pipefail

TAG="${1:-0.18.1}"
REPO_URL="https://github.com/yausername/youtubedl-android"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS_DIR="$PROJECT_ROOT/core/extractor/libs"
WORK_DIR="$(mktemp -d -t cosmic-ytdl-XXXXXX)"

cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT

echo "→ Cloning $REPO_URL @ $TAG into $WORK_DIR"
git clone --depth 1 --branch "$TAG" "$REPO_URL" "$WORK_DIR"

echo "→ Pointing at local Android SDK ($ANDROID_HOME)"
echo "sdk.dir=${ANDROID_HOME:-$HOME/Android/Sdk}" > "$WORK_DIR/local.properties"

echo "→ Building :library:assembleRelease and :common:assembleRelease"
( cd "$WORK_DIR" && ./gradlew :library:assembleRelease :common:assembleRelease --no-daemon )

mkdir -p "$LIBS_DIR"
cp "$WORK_DIR/library/build/outputs/aar/library-release.aar" \
   "$LIBS_DIR/youtubedl-android-library-${TAG}.aar"
cp "$WORK_DIR/common/build/outputs/aar/common-release.aar" \
   "$LIBS_DIR/youtubedl-android-common-${TAG}.aar"

echo
echo "✓ AARs in $LIBS_DIR:"
ls -lah "$LIBS_DIR"/*.aar
echo
echo "Reminder: update core/extractor/build.gradle.kts file paths if the tag"
echo "in the filename changes."
