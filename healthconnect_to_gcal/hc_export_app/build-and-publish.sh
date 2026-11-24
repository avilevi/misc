#!/usr/bin/env bash
#
# Build the HC Sync release APKs (phone + watch) locally and publish them to
# downloads.nvlf.uk, the same way the other apps in this repo do. The app's own
# updater reads https://downloads.nvlf.uk/hc-sync/version.json, so the
# versionCode produced here MUST increase monotonically.
#
# We keep a local counter in .build-number for that; it also catches up with
# whatever the live version.json already says, so a rollback cannot wedge the
# updater.
#
# Git is intentionally left alone: this script only builds + publishes. Commit
# and push code changes (including the bumped .build-number) yourself.
#
# Usage:  cd hc_export_app && ./build-and-publish.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Toolchain ───────────────────────────────────────────────────────────────
export JAVA_HOME="${JAVA_HOME:-/home/avil/java/jdk-17.0.19+10}"
export ANDROID_HOME="${ANDROID_HOME:-/home/avil/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ── Signing ─────────────────────────────────────────────────────────────────
KEYSTORE_PROPS="${KEYSTORE_PROPS:-$HOME/.android-keys/hc-sync-keystore.properties}"
if [ ! -f "$KEYSTORE_PROPS" ]; then
    echo "!! Missing signing config at $KEYSTORE_PROPS" >&2
    echo "   The release APK would be unsigned and refuse to install." >&2
    exit 1
fi
KEYSTORE_PATH="$(sed -n 's/^storeFile=//p' "$KEYSTORE_PROPS")"
KEYSTORE_PASSWORD="$(sed -n 's/^storePassword=//p' "$KEYSTORE_PROPS")"
KEY_ALIAS="$(sed -n 's/^keyAlias=//p' "$KEYSTORE_PROPS")"
[ -f "$KEYSTORE_PATH" ] || { echo "!! Keystore not found: $KEYSTORE_PATH" >&2; exit 1; }
export KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS

VERSION_MAJOR="${VERSION_MAJOR:-1}"
BUILD_NUMBER_FILE="$SCRIPT_DIR/.build-number"
DOWNLOADS_DIR="${DOWNLOADS_DIR:-/home/avil/downloads/hc-sync}"
BASE_URL="https://downloads.nvlf.uk/hc-sync"
APP_APK="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
WEAR_APK="$SCRIPT_DIR/wear/build/outputs/apk/release/wear-release.apk"

# ── Bump the monotonic build number ─────────────────────────────────────────
prev="$(tr -d '[:space:]' < "$BUILD_NUMBER_FILE" 2>/dev/null || echo 0)"
[ -z "$prev" ] && prev=0
remote="$(curl -s --max-time 15 "$BASE_URL/version.json" 2>/dev/null \
    | sed -n 's/.*"versionCode"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)"
[ -z "$remote" ] && remote=0
base=$(( prev > remote ? prev : remote ))
build_num=$(( base + 1 ))
version_name="${VERSION_MAJOR}.${build_num}"

echo "==> Building HC Sync  versionCode=$build_num  versionName=$version_name"
echo "    (local counter $prev, live version.json $remote)"

# ── Build (signing + env-var versioning live in build.gradle.kts) ──────────
BUILD_NUMBER="$build_num" VERSION_MAJOR="$VERSION_MAJOR" \
    ./gradlew --no-daemon :app:testDebugUnitTest :shared:testDebugUnitTest \
    :app:assembleRelease :wear:assembleRelease

for apk in "$APP_APK" "$WEAR_APK"; do
    [ -f "$apk" ] || { echo "!! Build reported success but APK not found: $apk" >&2; exit 1; }
done

# ── Verify the artifact, not the source ─────────────────────────────────────
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
if [ -x "$APKSIGNER" ]; then
    "$APKSIGNER" verify --print-certs "$APP_APK" | sed -n '1,3p'
else
    echo "!! apksigner not found; publishing an unverified APK" >&2
fi

# ── Publish to downloads.nvlf.uk ──────────────────────────────────────────
mkdir -p "$DOWNLOADS_DIR"
cp -f "$APP_APK" "$DOWNLOADS_DIR/app-release.apk"
cp -f "$WEAR_APK" "$DOWNLOADS_DIR/wear-release.apk"

build_date="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
cat > "$DOWNLOADS_DIR/version.json" <<EOF
{
  "versionCode": $build_num,
  "versionName": "$version_name",
  "buildDate": "$build_date"
}
EOF
cp -f "$DOWNLOADS_DIR/version.json" "$DOWNLOADS_DIR/wear-version.json"

# Persist the counter only after a fully successful build + publish.
echo "$build_num" > "$BUILD_NUMBER_FILE"

echo ""
echo "==> Published to $DOWNLOADS_DIR"
ls -la "$DOWNLOADS_DIR"/app-release.apk "$DOWNLOADS_DIR"/wear-release.apk
echo ""
echo "    https://downloads.nvlf.uk/hc-sync/version.json   (v$version_name, $build_date)"
echo "    https://downloads.nvlf.uk/hc-sync/app-release.apk?v=$build_num"
echo ""
echo "    Reminder: commit the bumped .build-number and your code changes yourself."
