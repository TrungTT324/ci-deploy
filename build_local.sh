#!/bin/bash

# Builds the appMouse debug APK, publishes it to the local web server, and
# updates its entry in app.json (portal card) plus appmouse-version.json (the
# in-app OTA update check via AppUpdateController) — no HTML is ever
# generated or touched here.
set -euo pipefail

echo "=== Building appMouse (debug) ==="
./gradlew :appMouse:assembleDebug | tee build_appmouse.log

BUILD_NO=$(grep -oE "APPMOUSE BUILD_NO GENERATED: [0-9]+" build_appmouse.log | grep -oE "[0-9]+" || true)
if [ -z "$BUILD_NO" ]; then
    BUILD_NO=$(date +%Y%m%d%H%M)
    echo "Warning: Could not extract BUILD_NO from build log. Falling back to current timestamp: $BUILD_NO"
fi
rm -f build_appmouse.log

VERSION_NAME=$(grep -oE 'versionName\s*=\s*"[^"]+"' appMouse/build.gradle.kts | cut -d'"' -f2 || echo "1.0.0")

SOURCE_APK="appMouse/build/outputs/apk/debug/appMouse-debug.apk"
if [ ! -f "$SOURCE_APK" ]; then
    echo "Error: appMouse debug APK was not generated at $SOURCE_APK"
    exit 1
fi

TARGET_DIR="/opt/homebrew/var/www/ci-deploy/apps"
mkdir -p "$TARGET_DIR"

# Filename carries the build timestamp, but the app still keeps exactly one
# APK on the server: every older appMouse_*.apk is removed before the new
# one is published.
TIMESTAMP=$(date +%Y_%m_%d_%H%M%S)
APK_NAME="appMouse_${TIMESTAMP}.apk"
DEST_APK="$TARGET_DIR/$APK_NAME"

find "$TARGET_DIR" -maxdepth 1 -name "appMouse_*.apk" -delete

echo "Publishing $APK_NAME to $TARGET_DIR..."
cp "$SOURCE_APK" "$DEST_APK.tmp"
mv "$DEST_APK.tmp" "$DEST_APK"

LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || ifconfig | grep "inet " | grep -v 127.0.0.1 | cut -d\  -f2 | head -n1 || echo "172.16.100.26")

APK_SIZE_BYTES=$(stat -f%z "$DEST_APK" 2>/dev/null || stat -c%s "$DEST_APK")
APK_SHA256=$(shasum -a 256 "$DEST_APK" | awk '{print $1}')

APP_JSON="/opt/homebrew/var/www/ci-deploy/app.json"
if [ -f "$APP_JSON" ] && command -v jq >/dev/null 2>&1; then
    echo "Updating AppMouse entry in $APP_JSON..."
    BUILD_DATE_LABEL=$(date "+%d-%b-%Y")
    APK_SIZE_MB=$(awk "BEGIN { printf \"%.1f\", $APK_SIZE_BYTES / 1048576 }")
    jq --arg date "$BUILD_DATE_LABEL" --argjson size "$APK_SIZE_MB" --arg url "apps/$APK_NAME" \
        '(.apps[] | select(.id == "appmouse")) |= (.buildDate = $date | .sizeMB = $size | .apkUrl = $url)' \
        "$APP_JSON" > "$APP_JSON.tmp"
    mv "$APP_JSON.tmp" "$APP_JSON"
fi

# In-app OTA check: AppUpdateController fetches this on launch and compares
# buildNo against BuildConfig.BUILD_NO, so it needs to be in lockstep with
# every publish, not just app.json (which only drives the portal page).
VERSION_JSON="$TARGET_DIR/appmouse-version.json"
echo "Updating $VERSION_JSON..."
cat <<EOF > "$VERSION_JSON.tmp"
{
  "appName": "AppMouse",
  "version": "$VERSION_NAME",
  "buildNo": $BUILD_NO,
  "buildNote": "Local build published via build_local.sh on $(date)",
  "url": "http://$LOCAL_IP:8080/ci-deploy/apps/$APK_NAME",
  "sha256": "$APK_SHA256",
  "sizeBytes": $APK_SIZE_BYTES,
  "packageName": "hdisoft.app.mouse"
}
EOF
mv "$VERSION_JSON.tmp" "$VERSION_JSON"

echo "=== Done ==="
echo "APK:     $DEST_APK"
echo "URL:     http://$LOCAL_IP:8080/ci-deploy/apps/$APK_NAME"
echo "Update:  http://$LOCAL_IP:8080/ci-deploy/apps/appmouse-version.json"
echo "Portal:  http://$LOCAL_IP:8080/ci-deploy/index.html"
