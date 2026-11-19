# Plazma S PC transfer engine v1

## Implemented in this milestone

- A Windows-capable Tokio TCP listener using rustls and TLS 1.3 only.
- A per-installation self-signed certificate and SHA-256 DER fingerprint, stored in the Tauri application data directory.
- mDNS-SD service registration only after the TLS listener has successfully bound and the certificate identity is ready.
- Discovery returns the advertised fingerprint but always marks peers unauthenticated.
- The sending client requires an explicit expected SHA-256 fingerprint and verifies the peer certificate during the TLS handshake. The TLS 1.3 handshake signature is verified by rustls; no trust-all verifier or plaintext fallback is used.
- A transfer offer is validated and surfaced to the receiver UI. The receiver must explicitly accept or reject before payload streaming.
- The listener caps concurrent unauthenticated connections at eight, rejects excess sockets, limits each source IP to twelve connection attempts per rolling minute, and times out TLS handshakes after ten seconds.
- Before asking the user to approve an offer, the receiver checks available disk space against the advertised transfer size and rejects transfers that cannot fit.
- Files are streamed in bounded 1 MiB chunks. Each chunk is SHA-256 checked and acknowledged; the complete file is checked against its manifest digest.
- Received data is written to an application-private partial file and promoted to its final sanitized filename only after checksum validation.
- Received files are inert; no file is executed or automatically opened.

## Wire format

The Rust DTOs in `src-tauri/src/protocol.rs` are authoritative. Kotlin uses kotlinx.serialization in `PlazmaTransferProtocol.kt` with explicit `@SerialName` mappings and `PlazmaProtocolJson` to encode/decode the Rust externally-tagged enum envelope:

```json
{"type":"offer","payload":{"protocolVersion":1,"transferId":"...","sender":{"deviceId":"...","displayName":"...","platform":"windows","certificateFingerprint":"...","protocolMin":1,"protocolMax":1},"capabilities":{"maxChunkBytes":1048576,"supportsResume":false,"supportsFolderManifest":false},"files":[],"totalBytes":0}}
```

DTO members use camelCase. Enum values use snake_case. Rust struct-variant fields for `cancel` and `state` remain snake_case inside the tagged payload. Optional DTO fields are emitted as explicit JSON nulls to match serde defaults.

## Pairing and trust model

- mDNS TXT values are discovery hints only and are attacker-controlled.
- The sender must compare the peer's fingerprint through a trusted out-of-band path before sending and then supply that exact fingerprint to the TLS connector.
- TLS certificate identity is pinned before application protocol messages are sent.
- The receiver independently approves every transfer offer.
- This first iteration authenticates the receiver to the sender using certificate pinning. It does not yet provide mutual TLS client-certificate authentication or persistent paired-device management. Sender display metadata must therefore be treated as unverified; do not silently trust it.
- A future pairing milestone must add mutual device authentication, durable trust revocation, QR/manual pairing UX, and replay/abuse limits before broad public release.

## Current limits

- PC↔PC regular-file transfer only. Folder manifests, pause/resume, persistent queue, transfer history, per-file progress UI, and bandwidth controls are not implemented.
- Sender file paths are currently entered manually in the desktop transfer panel.
- The Android client has the matching JSON codec and tests, but Android LAN transport is not yet implemented.
- No physical two-PC LAN test is claimed. Firewall prompts, Wi-Fi client isolation, multicast availability and large-file endurance require device testing.
- The mDNS dependency is beta software; manual IP entry remains the fallback.

## Required next security and reliability work

1. Add mutual TLS or an equivalent authenticated sender identity proof, plus explicit paired-device trust/revocation.
2. Add session-bound authentication tokens, comprehensive idle timeouts for every protocol phase, durable rate-limit state and abuse monitoring.
3. Strengthen collision-safe destination reservation under concurrent receives and add tests for concurrent filename races.
4. Add resume metadata and durable partial-file checkpoints.
5. Add file-picker UX, folder traversal with path-component validation, and a persistent transfer queue.
6. Run cross-platform fixture tests and physical two-PC LAN tests on different Windows firewall/network profiles.
