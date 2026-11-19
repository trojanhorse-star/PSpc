# Plazma S PC

Plazma S PC is a Windows-first desktop application for private, direct file transfers between computers on the same local network.

## Current implementation

This branch introduces the first native application scaffold:

- Tauri 2 desktop shell with a Rust backend.
- React + TypeScript frontend, with a responsive Plazma S PC dashboard.
- A working frontend-to-Rust command bridge (`get_app_status`).
- Windows NSIS and MSI bundle targets.
- A least-privilege starting capability set (`core:default` only).

The transfer engine, discovery, pairing, file access and media processing are **not implemented yet**. The UI labels these as upcoming rather than simulating successful transfers.

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

The generated MSI/NSIS installers are written under `src-tauri/target/release/bundle/`.

## Product scope

**Phase 1 — Windows to Windows:** local-network discovery, secure device pairing, bidirectional transfer, receive approval, resumable queues, duplicate handling, integrity verification, local file/media management, advanced conversion, optional sync and backup, recycle/recovery, tray and notifications.

**Later phases:** Windows ↔ Android, then Android ↔ Android. The first release remains Windows-only.

## Security principles

- No automatic execution of received files.
- Receiving-side approval before saving incoming files.
- Explicit pairing and trusted-device controls.
- Integrity verification before marking transfers complete.
- No cloud relay for ordinary PC-to-PC transfers.
- Network services must bind only to intended local interfaces and validate peer messages.

## Architecture direction

- Tauri 2 + Rust for native desktop and networking.
- React + TypeScript for the user interface.
- SQLite for local settings and transfer history.
- LAN discovery and an authenticated, encrypted peer protocol.
- BLAKE3 for file integrity verification.

See `docs/architecture/lan-transfer.md` for the first networking design notes.
