---
name: ci-deploy-rules
description: Workspace rules and context configuration for CI-Deploy Android App.
globs: "*"
---
# CI-Deploy Project Rules & Context

This workspace rule configures the Gemini Agent for the CI-Deploy Android project.

## 📌 Context
- **Project Type:** Native Android Kotlin application (No Flutter).
- **Package Name:** `hdisoft.app.cideploy`
- **Main Activity:** `MainActivity.kt` under `app/src/main/java/hdisoft/app/cideploy/MainActivity.kt`.
- **Target URL:** Dynamic discovery on local LAN port 8080 (fallback to saved host in SharedPreferences, checked via version JSON metadata).
- **Icon:** Rounded, light blue background `#90CAF9`, white text "CI" and "deploy" (ratio 7/3, deploy offset 1/2 down).

## 🛠️ Guidelines & Constraints
- **NO FLUTTER:** Never introduce `lib/`, `pubspec.yaml`, or run `flutter` commands. The project is 100% native Android Kotlin.
- **Host Discovery:** The app implements LAN scanning on port 8080. It launches 254 coroutines using a `Channel` for early exit upon finding the first active host. It verifies the host by fetching `http://<host>:8080/apps/ci-deploy/ci-deploy-version.json`.
- **OTA Updates:** The update metadata resides on the discovered host at `http://<discovered-host>:8080/apps/ci-deploy/ci-deploy-version.json`.
- **Build Commands:** Use `./gradlew assembleDebug` to compile the app.
- **Deployment:** Copy built APK to `/opt/homebrew/var/www/apps/ci-deploy/CI-Deploy_debug.apk` and update `/opt/homebrew/var/www/index.html`.
