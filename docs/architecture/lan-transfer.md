# LAN discovery and transfer protocol — initial design

## Scope

The first release discovers and transfers between Windows PCs on the same trusted local network. Internet connectivity is not required for ordinary transfers. The design must leave room for an Android client later without changing the core transfer protocol.

## Discovery

- Advertise a versioned Plazma service using DNS-SD/mDNS on the local link.
- Advertisements contain only a random device identifier, protocol version, display name and listening port. Never publish file names, user folders, account identifiers or pairing secrets.
- Treat all discovery data as untrusted hints. Discovery does not establish trust.
- Provide manual IP entry when multicast discovery is blocked.
- Use a short discovery timeout and avoid binding a listener to public interfaces by default.

## Pairing and trust

1. User explicitly starts pairing on both devices.
2. Peers exchange ephemeral public keys and a short-lived pairing challenge.
3. Users confirm a matching short authentication string/code on both screens.
4. Derive session keys using an authenticated key agreement; use a maintained TLS implementation rather than custom cryptography.
5. Persist a device identity/fingerprint and user-approved trust state locally.
6. Revoke trust immediately when a device is removed or blocked.

Pairing codes must be short-lived, rate-limited and single-use. Never log private keys, pairing secrets or session keys.

## Transfer session

- Receiver must approve each incoming transfer unless the user explicitly configures a trusted-device policy.
- Use an authenticated encrypted channel (TLS 1.3 or equivalent maintained implementation).
- Transfer metadata is bounded and validated before allocating resources.
- Stream files in chunks; do not load entire large files into memory.
- Record chunk progress and resume only after the peer and partial-file state are revalidated.
- Calculate BLAKE3 while streaming and verify the final digest before marking complete.
- Write to a temporary file in the destination directory, flush/sync, then atomically move into place after verification and conflict resolution.
- Prevent path traversal, symlink escapes, reserved Windows names and overwrite races.
- Never execute received files or installers automatically.

## Initial implementation sequence

1. Add discovery dependency and a Rust service with explicit start/stop lifecycle.
2. Expose discovered peer summaries to the UI; no automatic trust.
3. Add explicit pairing challenge and trust persistence.
4. Add a TLS listener/client and approval handshake.
5. Add chunked transfer, queue persistence, resume and BLAKE3 verification.
6. Add firewall guidance, network-change handling and integration tests using two Windows machines.

## Open engineering decisions

- Evaluate mDNS library maintenance, Windows multicast behaviour and licence before choosing a dependency.
- Decide whether QUIC is justified over TLS/TCP for the first stable release; prefer the simpler well-supported transport unless measurements justify QUIC.
- Define protocol compatibility/version negotiation before Android implementation.
