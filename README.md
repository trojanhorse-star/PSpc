# Plazma S

Plazma S is a family of private file and media transfer apps.

## Projects

- **Plazma S PC** — Windows desktop app. See the existing desktop documentation below.
- **Plazma S Android** — native Android companion in [android/](android/). The Android edition is scoped to files and media only; app migration is excluded.

## Android

See [android/README.md](android/README.md) for prerequisites, build instructions and current implementation status.

## Windows desktop

The Windows foundation is maintained in `src-tauri/` with a React + TypeScript interface. Run the desktop app with:

```bash
npm install
npm run tauri dev
```

See the desktop architecture notes in [docs/architecture/lan-transfer.md](docs/architecture/lan-transfer.md).
