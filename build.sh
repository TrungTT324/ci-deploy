#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -euo pipefail

echo "=== Starting CI-Deploy Build Process ==="

# 1. Build the debug artifact used by OTA and local device testing.
echo "Building debug APK..."
./gradlew assembleDebug | tee build.log

# 2. Extract buildNo and versionName
echo "Extracting build metadata..."
BUILD_NO=$(grep -oE "CI-DEPLOY BUILD_NO GENERATED: [0-9]+" build.log | grep -oE "[0-9]+" || true)

if [ -z "$BUILD_NO" ]; then
    # Fallback in case grep fails
    BUILD_NO=$(date +%Y%m%d%H%M)
    echo "Warning: Could not extract BUILD_NO from build log. Falling back to current timestamp: $BUILD_NO"
else
    echo "Extracted BUILD_NO: $BUILD_NO"
fi

VERSION_NAME=$(grep -oE "versionName\s*=\s*\"[^\"]+\"" app/build.gradle.kts | cut -d'"' -f2 || echo "1.0.0")
echo "Extracted Version: $VERSION_NAME"

# Clean up build log
rm -f build.log

# 3. Define target directory and copy APK
TARGET_DIR="/opt/homebrew/var/www/ci-deploy"
echo "Ensuring destination directory exists: $TARGET_DIR"
mkdir -p "$TARGET_DIR"

DEBUG_SOURCE_APK="app/build/outputs/apk/debug/app-debug.apk"
DEBUG_DEST_APK="$TARGET_DIR/CI-Deploy_debug.apk"

if [ ! -f "$DEBUG_SOURCE_APK" ]; then
    echo "Error: Debug APK was not generated."
    exit 1
fi

echo "Publishing debug APK to $DEBUG_DEST_APK..."
cp "$DEBUG_SOURCE_APK" "$DEBUG_DEST_APK.tmp"
mv "$DEBUG_DEST_APK.tmp" "$DEBUG_DEST_APK"

# 4. Determine Local IP
LOCAL_IP=$(ipconfig getifaddr en0 || ipconfig getifaddr en1 || ifconfig | grep "inet " | grep -v 127.0.0.1 | cut -d\  -f2 | head -n1 || echo "172.16.100.26")
echo "Using Local IP: $LOCAL_IP"

# 5. Generate and write the version JSON file
VERSION_JSON="$TARGET_DIR/ci-deploy-version.json"
echo "Updating version JSON at $VERSION_JSON..."
APK_SHA256=$(shasum -a 256 "$DEBUG_DEST_APK" | awk '{print $1}')
APK_SIZE=$(stat -f%z "$DEBUG_DEST_APK" 2>/dev/null || stat -c%s "$DEBUG_DEST_APK")

cat <<EOF > "$VERSION_JSON.tmp"
{
  "appName": "CI-Deploy",
  "version": "$VERSION_NAME",
  "buildNo": $BUILD_NO,
  "buildNote": "Build OTA debug APK via build.sh on $(date)",
  "url": "http://$LOCAL_IP:8080/ci-deploy/CI-Deploy_debug.apk",
  "sha256": "$APK_SHA256",
  "sizeBytes": $APK_SIZE,
  "packageName": "hdisoft.app.cideploy"
}
EOF
mv "$VERSION_JSON.tmp" "$VERSION_JSON"

echo "=== Build and Deploy Successful ==="
echo "Version: $VERSION_NAME"
echo "Build No: $BUILD_NO"
echo "Debug APK: http://$LOCAL_IP:8080/ci-deploy/CI-Deploy_debug.apk"
echo "OTA APK: http://$LOCAL_IP:8080/ci-deploy/CI-Deploy_debug.apk"
