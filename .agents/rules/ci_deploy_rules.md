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
- **Target URL:** `http://172.16.100.26:8080` (loaded via WebView).
- **Icon:** Rounded, light blue background `#90CAF9`, white text "CI" and "deploy" (ratio 7/3, deploy offset 1/2 down).

## 🛠️ Guidelines & Constraints
- **NO FLUTTER:** Never introduce `lib/`, `pubspec.yaml`, or run `flutter` commands. The project is 100% native Android Kotlin.
- **OTA Updates:** The update metadata resides on the local web server at `http://172.16.100.26:8080/apps/ci-deploy/ci-deploy-version.json`.
- **Build Commands:** Use `./gradlew assembleDebug` to compile the app.
- **Deployment:** Copy built APK to `/opt/homebrew/var/www/apps/ci-deploy/CI-Deploy_debug.apk` and update `/opt/homebrew/var/www/index.html`.
