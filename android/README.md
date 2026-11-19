# Plazma S Android

Native Android companion app for Plazma S. This edition is intentionally limited to **files and media**. It does not send, receive, clone, or migrate installed applications.

## Current foundation

- Kotlin + Jetpack Compose Android application.
- Dark neon Plazma S interface with Home, Files, Media, Activity and Settings areas.
- Android Storage Access Framework pickers for user-selected files, images, video and audio.
- Receives files shared to it through Android's Sharesheet and copies them into its private app inbox.
- No broad storage permission and no automatic execution of received files.
- No cloud upload.

## Build

Requirements: JDK 17, Android SDK platform 35, Android Build Tools, and Gradle 8.9.

From this directory:

```bash
gradle assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Current limitations

The UI and local file intake foundation are in place. Network device discovery, secure pairing, PC/Android transfer protocol, resumable transfers, transfer history persistence, media playback/conversion and release signing are not implemented yet. The Android app must not present a transfer as complete until the shared transfer engine is integrated and verified.


## Transfer engineering primitives

The Android source now includes LocalSend-v2-inspired protocol DTOs and a streaming SHA-256 helper in `transfer/TransferProtocol.kt`. `InstalledAppCatalog.kt` enumerates launchable apps visible through Android's package-visibility query, records whether split APKs exist, and exports the base APK to a private cache location with a FileProvider URI. These are reusable primitives, not a completed network sender/receiver or finished APK picker screen. Split APK bundling and user-confirmed installation remain follow-on work.
