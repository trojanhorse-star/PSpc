# Plazma S PC

Plazma S is a cross-device, peer-to-peer file-transfer product. The Windows application is the first implementation target.

## Current implementation

- Tauri 2 desktop shell with a Rust backend.
- React + TypeScript frontend and responsive Plazma S PC dashboard.
- Frontend-to-Rust command bridge (`get_app_status`).
- Windows NSIS and MSI bundle targets.
- Versioned, platform-neutral transfer protocol v1 DTOs and manifest validation tests.
- TLS 1.3 receiver with per-installation certificate identity and SHA-256 certificate pin verification on the sending PC.
- mDNS service advertisement only while the secure receiver is active; discovery results remain unauthenticated until fingerprint verification.
- Explicit receiver approval, bounded chunk streaming, per-chunk acknowledgements and whole-file SHA-256 verification.
- Android Kotlin JSON codec using kotlinx.serialization, with Rust-compatible field casing, enum values and tagged control-message envelopes.
- Desktop send/receive controls for discovery, manual IP/fingerprint entry, sending regular files and approving incoming offers.
- Protocol specifications at `docs/architecture/plazma-transfer-protocol-v1.md` and `docs/architecture/pc-transfer-engine-v1.md`.

This is an initial PC↔PC file-transfer milestone, not the finished product. Mutual TLS sender authentication, persistent pairing/revocation, folders, pause/resume, durable queues, progress history, and Android network transport remain follow-up work. No physical two-PC transfer test is claimed yet.

## Product scope

**Phase 1 — PC ↔ PC:** Windows computers can both send and receive. Implement discovery, secure pairing, explicit receive approval, chunked streaming, resumable queues, duplicate handling, integrity verification, local file/media management, advanced conversion, optional sync and backup, recycle/recovery, tray and notifications.

**Phase 2 — PC ↔ Android:** Windows and Android devices can both send and receive using the same protocol.

**Phase 3 — Android ↔ Android:** Android devices can both send and receive using the same protocol.

The first Android release remains focused on files and media. APK transfer and installation are separate future capabilities.

## Development prerequisites

- Node.js 20+ and npm.
- Rust stable toolchain.
- Windows: Microsoft C++ Build Tools and WebView2 Runtime.
- Tauri v2 platform prerequisites: https://v2.tauri.app/start/prerequisites/

## Run locally

```bash
npm install
npm run tauri dev
```

## Build a Windows installer

```bash
npm install
npm run tauri build
```

Installers are written under `src-tauri/target/release/bundle/`.

## Security principles

- TLS 1.3 with pinned peer identity after explicit pairing; no plaintext fallback.
- Receiver approval before incoming content is accepted.
- Bounded manifests and chunks; validate names and paths on the receiver.
- SHA-256 verification before promoting temporary files to final destinations.
- Never automatically execute received files.
- No cloud relay for ordinary local-network transfers.
- Discovery announcements are untrusted hints, never proof of identity.

## Architecture

- Tauri 2 + Rust for Windows native networking and file operations.
- Kotlin + Jetpack Compose for Android.
- React + TypeScript for the Windows user interface.
- SQLite/Room for durable transfer state and trusted devices.
- DNS-SD/mDNS discovery, manual-IP and QR fallbacks.
- A single versioned bidirectional protocol for every platform pairing.

See:
- `docs/architecture/lan-transfer.md`
- `docs/architecture/plazma-transfer-protocol-v1.md`
