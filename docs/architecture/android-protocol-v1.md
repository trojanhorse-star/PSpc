# Plazma S Transfer Protocol v1 — Android implementation notes

Android implements the same protocol as the Windows client. The canonical transport and security contract is documented in the Windows protocol specification; the Kotlin DTOs and validation are mirrored in `android/app/src/main/java/com/plazmas/android/transfer/PlazmaTransferProtocol.kt`.

Wire contract:
- JSON object keys are camelCase.
- Enum values are snake_case.
- Control messages use `{"type":"offer","payload":{...}}` (or the corresponding message type).
- Device platform values: `windows`, `android`, `linux`, `macos`, `other`.
- Both sender and receiver roles are supported on every platform.

Android-specific rules:
- Keep the first Android release limited to files and media.
- Use the Storage Access Framework for selected source and destination URIs.
- Run user-visible long transfers in a foreground service.
- Do not treat mDNS discovery as authentication; pin the TLS fingerprint after explicit pairing.
- Require receiver approval before accepting an offer.
- Write to private temporary storage and promote to the destination only after final size and SHA-256 verification.
- Never auto-install APKs or execute received files.

The DTO mirror is not a network client/server. TLS, discovery, pairing, transfer persistence and streaming remain subsequent milestones.
