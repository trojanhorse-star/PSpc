# Transfer modes and application-transfer policy

Plazma S exposes route-specific transfer modes from the plus (+) transfer selector. A route is a distinct capability boundary; the UI label is not the security boundary.

## Supported routes and policy

| Route | Ordinary files | Media | APK / app packages | Install apps |
|---|---:|---:|---:|---:|
| PC ↔ PC | Yes | Yes | Yes | Receiving PC only, with user action |
| PC → Android | Yes | Yes | No | No |
| Android ↔ Android | Yes | Yes | Yes | Receiving Android only, with Android's user-approved installer flow |
| Android → PC | Yes | Yes | No | No |

## Enforcement requirements

- Enforce route policy on both sender and receiver. Hiding an item in a picker is not sufficient.
- PC-to-Android and Android-to-PC receivers must reject APK, APKS, AAB, XAPK, Android package MIME types, and app-package transfer manifest types regardless of filename.
- PC-to-PC and Android-to-Android may transfer app package files only after explicit receiver approval.
- Transferring an app package is not the same as silently installing it. Installation is a separate, receiver-controlled action using the platform's supported installer.
- Never auto-install software on a recipient device.
- All routes retain TLS, receiver approval, bounded transfer resources, and per-chunk/final integrity verification.
- Each route has a separate capability declaration so a sender cannot claim app-transfer permission merely by choosing a different UI screen.

## User experience

- PC app: the plus (+) action opens **PC ↔ PC** and **PC → Android**.
- Android app: the plus (+) action opens **Android ↔ Android** and **Android → PC**.
- The selected route remains visible during the session and is included in transfer confirmation UI.
- Unsupported routes must be clearly labelled as in development rather than presenting non-functional send controls.
