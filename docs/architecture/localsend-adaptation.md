# LocalSend engineering reference and Plazma S adaptation

## Source reviewed

- Upstream app: https://github.com/localsend/localsend (repository declares Apache-2.0).
- Protocol: https://github.com/localsend/protocol (v2.2 documentation reviewed).
- Discovery: `packages/core/src/discovery/mod.rs` implements multicast announcements, HTTP registration, channel observations and discovered-device state.
- Device identity: `packages/core/src/crypto/cert.rs` creates a self-signed certificate and SHA-256 fingerprint.
- Peer verification: `packages/core/src/http/client/server_cert_verifier.rs` validates the certificate and can pin the expected fingerprint.
- Streaming: `packages/core/src/model/transfer.rs` uses bounded streams and supports Android raw file descriptors.
- Upload orchestration: `packages/localsend_isolates/lib/src/task/upload/http_upload.dart`.
- Receive saving: `packages/localsend_isolates/lib/src/task/server/file_saver.dart`.
- Receive approval: `app/lib/provider/network/server/controller/receive_controller.dart`.
- Android APK picker: `app/lib/provider/apk_provider.dart`, `app/lib/pages/apk_picker_page.dart`, and `app/lib/util/native/cross_file_converters.dart`.

## What the LocalSend protocol gives us

Protocol v2.2 is a local-network REST protocol with no external server requirement. Its documented defaults are UDP multicast `224.0.0.167:53317` and HTTP/TCP port `53317`. It announces device metadata and a certificate fingerprint, registers peers, prepares an upload using a metadata-only manifest, waits for receiver acceptance, then streams binary files using a session ID and per-file tokens. When a SHA-256 is supplied, the receiver verifies it and rejects mismatches. It also defines cancellation and reverse download endpoints.

These are useful foundations, but LocalSend's base upload endpoint is not a resumable-chunk protocol. Plazma S must add a versioned chunk/resume extension for interrupted large transfers rather than falsely describing the base protocol as resumable.

## App transfer findings

LocalSend's Android source includes an installed-app picker. It enumerates installed applications using the `device_apps` package, reads `Application.apkFilePath`, and converts that path into a normal file named with the app name and version. The transfer DTO recognizes the Android APK MIME type `application/vnd.android.package-archive`.

Plazma S should follow this pattern: APKs are file payloads, not remote install commands. The first version can export an installed app's base APK and send it as a normal file. The picker must flag split APK packages; base-only export may not install correctly for those apps. A later bundle format can package base plus split APKs and install them through Android's user-confirmed package installer flow.

APK transfer does not copy app-private data, accounts, licences, device-bound keys, or protected content. Receiving an APK must never auto-install or auto-run it. The user should explicitly open the Android package installer and approve installation. Google Play distribution also requires careful review of package visibility and REQUEST_INSTALL_PACKAGES policy.

## License and reuse decision

The LocalSend app repository declares Apache-2.0. This branch uses its protocol and source as an engineering reference and does not copy LocalSend UI, branding, or substantial source files. Any future direct code reuse must preserve Apache-2.0 notices, include required license/NOTICE material, record modifications, and audit transitive dependency licences. The protocol repository's own licensing metadata must be checked separately before copying implementation code.

## Plazma S implementation design

1. Keep the Android client native Kotlin/Jetpack Compose and the Windows client Tauri/Rust/React.
2. Define a shared, versioned Plazma S protocol contract, using LocalSend v2 concepts where compatible.
3. Give every installation a persistent private key and self-signed certificate. Use SHA-256 certificate fingerprints as device identity; pin fingerprints after explicit pairing.
4. Discover peers with UDP multicast and HTTP registration; offer manual IP entry when multicast is unavailable. Treat discovery announcements as untrusted until TLS registration succeeds.
5. Make the receiver host HTTPS upload endpoints. Send a metadata-only manifest first, show an explicit approval screen, and issue random session/file tokens only for accepted files.
6. Stream raw bytes to `.part` files with bounded concurrency. Validate names and paths, enforce quotas and free-space checks, verify byte count and SHA-256, then atomically rename into the chosen destination.
7. Add transfer queue, per-file progress, speed/ETA, cancel, retry and a Plazma chunk protocol for pause/resume.
8. Use Android's Storage Access Framework for user-selected source and destination access. Use app-private staging for untrusted incoming data until validation completes.
9. Add an Android installed-app picker and APK export. Start with base APK support and split-package warnings; then implement an `.apks` bundle and explicit package-installer handoff.
10. No cloud dependency for local transfers. Internet is needed only for optional updates.

## Engineering milestones

- M1: protocol DTOs, certificate identity, TLS pinning tests and cross-platform protocol fixtures.
- M2: Android and PC LAN discovery plus manual-IP fallback.
- M3: secure pairing and bidirectional small-file transfer with explicit receiver approval.
- M4: large-file streaming, integrity verification, cancel/retry and resume.
- M5: media batches and installed APK export/transfer, including split APK bundles.
- M6: interoperability tests, Android lifecycle/background transfer tests, Windows firewall handling, threat review and release licence audit.

## Security and compatibility tests

- Reject malformed announcements, stale/invalid session tokens, path traversal, duplicate IDs and oversized manifests.
- Verify that a mismatched certificate fingerprint is rejected before file metadata or content is sent.
- Verify that a checksum mismatch never promotes a `.part` file to the final destination.
- Test permission denial, revoked SAF grants, low storage, app backgrounding, network changes and interrupted transfers.
- Test APK receive as inert data; installation occurs only after an explicit user action.
- Test simple APKs and split APKs separately and report unsupported packages clearly.