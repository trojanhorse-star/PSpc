# Architecture

Plazma S is Windows-first and LAN-first. The desktop client should work without requiring a cloud account or routing ordinary file contents through a hosted service.

## Proposed stack
- Tauri 2: native desktop shell and Windows integration.
- React + TypeScript: interface.
- Rust: device discovery, pairing, transfer protocol, file operations, and OS integration.
- SQLite: local settings, trusted devices, transfer queue, and history.
- BLAKE3: streaming file integrity checks and duplicate fingerprints.

## Security principles
- Pair devices using a short-lived code or QR and require explicit approval.
- Authenticate peers and encrypt transport sessions.
- Never trust a device merely because it is on the same LAN.
- Validate paths and prevent path traversal when writing received files.
- Use temporary files and atomic finalization; verify before marking a transfer complete.
- Keep secrets out of logs and avoid exposing received files to other users by default.

## Transfer lifecycle
1. Discover or manually enter a peer address.
2. Pair and verify the peer identity.
3. Negotiate a transfer manifest and destination policy.
4. Stream data in bounded chunks with resumable checkpoints.
5. Verify the received content hash.
6. Atomically move into the destination and record the result.

## App migration caution
Copying an application's installation folder is not a reliable migration strategy. Each supported application needs an adapter for settings/data and must account for services, registry entries, dependencies, licensing, and permissions. Unsupported apps should offer installer transfer or a clear manual migration path.

## Current implementation status
The first commit is a web-based UI foundation and planning documents. It is not yet a Tauri application and has no working discovery, pairing, transfer, conversion, backup, or startup integration.