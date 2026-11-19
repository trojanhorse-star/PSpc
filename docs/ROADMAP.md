# Plazma S PC roadmap

## Phase 1 — Windows foundation
- [x] Product decisions and initial UI foundation
- [x] Responsive dashboard concept and named palette selector
- [ ] Tauri 2 shell and Windows packaging
- [ ] Persist settings and transfer history with SQLite
- [ ] Startup, tray, and native notifications

## Phase 2 — Discovery and pairing
- [ ] Local network device discovery
- [ ] Manual IP fallback and pairing code / QR flow
- [ ] Explicit device approval and authenticated encrypted sessions

## Phase 3 — Transfer engine
- [ ] File and folder send/receive
- [ ] Queue, pause, resume, retry, and cancellation
- [ ] Automatic destination saving
- [ ] BLAKE3 integrity verification and duplicate detection

## Phase 4 — File management
- [ ] Optional folder synchronisation
- [ ] Conflict handling and transfer history
- [ ] Recycle/recovery and backup workflows

## Phase 5 — Apps and media
- [ ] Installed application inventory and migration adapters
- [ ] Installer/package transfer
- [ ] Advanced media conversion, including MP4 to MP3
- [ ] LGPL-compliant FFmpeg distribution plan

## Phase 6 — Hardening and release
- [ ] Threat model, permission review, and security tests
- [ ] Large-file, interruption, and network stress tests
- [ ] Signed Windows installer and update channel
- [ ] Windows 10/11 compatibility validation

## Later platform expansion
- PC-to-Android
- Android-to-Android

> Current UI actions are previews. They do not yet perform native file operations or network transfers.