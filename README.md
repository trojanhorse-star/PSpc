# Plazma S PC

Plazma S is a Windows-first, peer-to-peer file and app migration experience. The first release focuses on secure PC-to-PC transfers over a local network. Mobile-to-PC and Android-to-Android support are planned for a later phase.

## Project status

The repository is being initialized. The first milestone is the Windows desktop foundation and a polished dashboard; transfer networking and file operations will be added in subsequent milestones.

## Product decisions

- Optional folder synchronisation.
- Advanced media conversion, including MP4 to MP3.
- Automatic saving of received files to the laptop.
- Windows startup enabled by default, with a user setting to change it.
- Duplicate detection, transfer queue, integrity verification, security controls, recycle/recovery, backup, system tray, and notifications.
- Named colour palettes rather than hex-code selection in the user interface.
- PC-to-Android and Android-to-Android are later roadmap items.

## Planned architecture

- Tauri 2 desktop shell.
- React + TypeScript interface.
- Rust transfer and system-integration core.
- SQLite for local transfer history and settings.
- Encrypted peer-to-peer connections with explicit device pairing and approval.
- BLAKE3-based file integrity verification.

## Development

Development setup instructions will be added with the application scaffold. The app is not yet a working file-transfer client.
