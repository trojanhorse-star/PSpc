# Plazma S PC silent updates

## Runtime behaviour
- The Tauri updater checks the stable GitHub Releases manifest at application startup.
- If a newer signed release is available, it downloads and verifies the package, installs quietly, and relaunches Plazma S before the workspace is available.
- Opening Update Centre performs an on-demand check and displays the installed/available version.
- Startup installation means no transfer can be active yet; updates do not interrupt file transfers.
- Tauri rejects packages without a valid signature.

## One-time release signing setup (required before publishing updates)
1. On a trusted maintainer machine, run: `npm run tauri signer generate -- -w ~/.tauri/plazma-s.key`.
2. Keep the generated private key and password in a secure password manager/backup. Never commit or upload the private key to the repository.
3. Copy only the generated public key into `src-tauri/tauri.conf.json` in `plugins.updater.pubkey`. The release overlay inherits this setting.
4. Add the private key as the GitHub Actions repository secret `TAURI_SIGNING_PRIVATE_KEY`. If password-protected, add `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`.
5. Protect the release workflow and restrict who can publish tags/releases.

The public key is deliberately a setup placeholder in this branch. Until it is replaced with the matching public key and Actions secrets are configured, production updates cannot authenticate packages. Release publishing is gated.

## Publishing
Update both `package.json` and `src-tauri/Cargo.toml` to the exact version represented by the tag (for example, `0.2.0` for `v0.2.0`). Push a version tag such as `v0.2.0`. The release workflow validates the tag/version match, builds signed NSIS updater artifacts, and publishes `latest.json` plus installer/signature assets. Manual workflow dispatch is accepted only when run against a tag. MSI remains a standalone download; quiet in-place updates use the per-user NSIS install channel.

## Windows installation scope
Tauri Windows `quiet` install mode cannot elevate by itself. Silent updates therefore require a per-user NSIS installation. Existing machine-wide/MSI installations must be migrated or use a different update mode before enrolling in this channel. Do not claim silent updates for those installations.

## Operational safeguards
- Never publish unsigned artifacts to the stable channel.
- Keep the signing private key out of build logs and artifacts.
- Test a real upgrade from the previous published version on a clean Windows user account before announcing a release.
- Keep the previous release available for manual recovery.

## Remaining release gate
The updater implementation is compiled in CI, but a real signed release/upgrade has not been exercised. This cannot be completed until the maintainer supplies the public key in the repository and configures the private key as an Actions secret. Never send the private key in chat or commit it.
