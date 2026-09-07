# Faithbook V19 Android Build Candidate

This candidate is prepared for an actual Android Gradle build.

## Identity
- App: Faithbook
- package/applicationId: `com.arkforge.faith` (preserved for continuity)
- versionCode: `190001`
- versionName: `19.0.0-foundation`
- compileSdk/targetSdk: 35
- minSdk: 26
- Java: 17
- AGP: 8.9.2
- recommended Gradle: 8.11.1

## Privacy hardening in this candidate
The public candidate declares only `INTERNET` and `ACCESS_NETWORK_STATE`. Device GPS and microphone recording UI/bridge code were removed from the public candidate rather than adding sensitive permissions. Map handoff still works with coordinates entered by the user; phone/SMS/email use explicit external intents and do not request contacts/SMS/call-log permissions. Android cloud backup is disabled; Faithbook uses its explicit export/restore path instead.

## Local build
Open the folder in a current Android Studio installation and choose **Build > Build APK(s)**, or configure the SDK and Gradle 8.11.1 and run `./BUILD_DEBUG.sh` (the script uses system Gradle; a wrapper JAR is not bundled).

## Cloud build
The included `.github/workflows/android-debug.yml` installs Java 17, Android SDK 35, Gradle 8.11.1, runs the source verifier, builds `app-debug.apk`, hashes it, and uploads it as a GitHub Actions artifact.

## Promotion boundary
A successful compile is only one gate. Do not call this release-ready until installation, launch, persistence, import/export, Scripture install/search, Constructor, Ancestors, Alexandria, backup/restore, continuity handling, and rollback are tested on an Android device. A signed release AAB and Play review remain separate gates.
