#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then echo "ERROR: Java 17 is required."; exit 2; fi
if ! command -v gradle >/dev/null 2>&1; then echo "ERROR: Gradle 8.11.1 is required (or build from Android Studio)."; exit 2; fi
if [ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ] && [ ! -f local.properties ]; then echo "ERROR: Android SDK path is not configured."; exit 2; fi
gradle --no-daemon :app:assembleDebug --stacktrace
APK=app/build/outputs/apk/debug/app-debug.apk
if [ ! -f "$APK" ]; then echo "ERROR: Gradle finished without expected APK: $APK"; exit 3; fi
cp -f "$APK" Faithbook-debug.apk
sha256sum Faithbook-debug.apk | tee Faithbook-debug.apk.sha256
printf '
Built: %s
' "$(pwd)/Faithbook-debug.apk"
