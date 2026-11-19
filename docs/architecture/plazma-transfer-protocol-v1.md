# Plazma S Transfer Protocol v1

Status: implementation contract for the first shared PC-to-PC engine and the later Android clients.

## Supported connection routes

Every endpoint is both a sender and receiver. The same protocol supports:

- Windows PC ↔ Windows PC (first implementation target).
- Windows PC ↔ Android (next integration target).
- Android ↔ Android (later integration target).

Do not create separate transfer implementations for these routes.

## Network model

- Local peer discovery uses DNS-SD/mDNS service type `_plazma-s._tcp.local.`.
- Discovery is a hint only. Advertisements contain a random installation ID, protocol range, display label, TLS fingerprint and listening port; never file names, paths or secrets.
- Manual IP/port entry and QR pairing are required fallbacks when multicast is unavailable.
- The receiver hosts the authenticated endpoint. Either peer may host it for a given transfer.
- Use TLS 1.3 with a maintained implementation. Pin the peer certificate fingerprint after explicit pairing. Do not downgrade to plaintext HTTP.
- A discovery response never implies trust. Pairing and receiver approval are separate actions.

## Pairing

1. Both users explicitly enter pairing mode.
2. Peers exchange ephemeral pairing material over TLS and display the same short authentication code.
3. Both users confirm the matching code.
4. Store the peer's certificate fingerprint and user-approved device identity locally.
5. Pairing challenges are short-lived, single-use and rate-limited. Provide revoke/block controls.

Do not implement custom cryptographic primitives or treat a six-digit code as an encryption key. Use a maintained TLS/key-agreement library and bind the user confirmation to the authenticated handshake.

## Transfer lifecycle

1. Sender submits a bounded metadata-only manifest.
2. Receiver validates protocol version, file count, aggregate size, names and available storage.
3. Receiver explicitly accepts or rejects the offer.
4. Accepted transfers receive a random, short-lived session token scoped to that transfer.
5. Files stream in bounded chunks to temporary `.part` files.
6. Each chunk is acknowledged with its offset and digest. Resume only from a verified offset after reconnect and re-authentication.
7. Receiver verifies final size and SHA-256, flushes data, then atomically moves it to the chosen destination.
8. Completed, rejected, cancelled and failed states are persisted locally.

## Limits and validation

- Initial maximum manifest: 10,000 files.
- Initial maximum chunk: 4 MiB; negotiate a lower value when a peer advertises less.
- Reject integer overflow, duplicate file IDs, malformed digests, oversized metadata, invalid offsets and unknown transfer IDs.
- File names are display metadata, not paths. Folder paths are relative paths and must be validated component by component.
- Reject absolute paths, drive prefixes, UNC paths, dot-dot traversal, reserved Windows names and symlink escapes.
- Never overwrite silently. Resolve duplicates with rename, skip or explicit replace.
- Never execute or auto-open received content.

## Persistence and recovery

Persist transfer ID, peer identity, file ID, source/destination reference, expected size/hash, confirmed offset and state. Do not persist session tokens or private keys in logs. On resume, re-authenticate the peer, validate the partial file length and re-hash the confirmed prefix or verified chunks before continuing.

## Android and Windows responsibilities

### Windows (Tauri + Rust)

- Rust owns discovery, listener lifecycle, TLS, file streaming, queue state and filesystem validation.
- React is a presentation layer and must not receive private keys or session tokens.
- Use SQLite for device trust and transfer history.
- Provide Windows Firewall guidance when inbound discovery/listening is blocked.

### Android (Kotlin + Jetpack Compose)

- Kotlin implements the same DTOs and state transitions.
- Use a foreground service for active user-initiated transfers and Room for durable state.
- Use Storage Access Framework grants for user-selected sources/destinations.
- Keep Android↔PC and Android↔Android support in the protocol from the start.
- The initial Android release transfers files and media only. APK export/install is a later, separate capability.

## Compatibility and test contract

- Version negotiation is mandatory. Reject unsupported major versions.
- Keep JSON fixtures shared between Rust and Kotlin to detect serialization drift.
- Test malformed manifests, duplicate IDs, path traversal, certificate mismatch, token replay, chunk overlap, truncated chunks, wrong hashes, low disk space, network changes, process restarts and cancelled transfers.
- End-to-end acceptance tests must include two Windows machines on a real LAN before calling PC↔PC complete.
