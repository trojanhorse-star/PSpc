//! Versioned, platform-neutral Plazma S transfer contract.
//!
//! This module contains wire DTOs only. It deliberately does not implement
//! networking or cryptography; transport must be authenticated TLS 1.3 and
//! device identity must be pinned after explicit user pairing.

use serde::{Deserialize, Serialize};

pub const PROTOCOL_VERSION: u16 = 1;
pub const MAX_MANIFEST_FILES: usize = 10_000;
pub const MAX_CHUNK_BYTES: u32 = 4 * 1024 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum DevicePlatform {
    Windows,
    Android,
    Linux,
    Macos,
    Other,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct DeviceIdentity {
    pub device_id: String,
    pub display_name: String,
    pub platform: DevicePlatform,
    /// SHA-256 fingerprint of the device's TLS certificate (lowercase hex).
    pub certificate_fingerprint: String,
    pub protocol_min: u16,
    pub protocol_max: u16,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct PeerCapabilities {
    pub max_chunk_bytes: u32,
    pub supports_resume: bool,
    pub supports_folder_manifest: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct FileManifestEntry {
    pub file_id: String,
    /// Display name only; never accept this as a filesystem path.
    pub file_name: String,
    pub size_bytes: u64,
    pub mime_type: Option<String>,
    /// SHA-256 of the complete source file, when known.
    pub sha256: Option<String>,
    /// Relative folder path, validated component-by-component by the receiver.
    pub relative_path: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct TransferOffer {
    pub protocol_version: u16,
    pub transfer_id: String,
    pub sender: DeviceIdentity,
    pub capabilities: PeerCapabilities,
    pub files: Vec<FileManifestEntry>,
    pub total_bytes: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum TransferDecisionKind {
    Accept,
    Reject,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct TransferDecision {
    pub transfer_id: String,
    pub decision: TransferDecisionKind,
    /// Receiver-issued, short-lived opaque token; never a reusable password.
    pub session_token: Option<String>,
    pub reason_code: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct ChunkRequest {
    pub transfer_id: String,
    pub file_id: String,
    pub offset_bytes: u64,
    pub length_bytes: u32,
    /// SHA-256 for this chunk, allowing corruption detection during resume.
    pub chunk_sha256: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct ChunkAcknowledgement {
    pub transfer_id: String,
    pub file_id: String,
    pub next_offset_bytes: u64,
    pub chunk_sha256: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum TransferState {
    Queued,
    AwaitingApproval,
    Transferring,
    Verifying,
    Complete,
    Rejected,
    Cancelled,
    Failed,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(tag = "type", content = "payload", rename_all = "snake_case")]
pub enum ControlMessage {
    Offer(TransferOffer),
    Decision(TransferDecision),
    ChunkRequest(ChunkRequest),
    ChunkAcknowledgement(ChunkAcknowledgement),
    Cancel { transfer_id: String },
    State { transfer_id: String, state: TransferState },
}

pub fn validate_offer(offer: &TransferOffer) -> Result<(), &'static str> {
    if offer.protocol_version != PROTOCOL_VERSION {
        return Err("unsupported_protocol_version");
    }
    if offer.files.is_empty() || offer.files.len() > MAX_MANIFEST_FILES {
        return Err("invalid_file_count");
    }
    if offer.capabilities.max_chunk_bytes == 0
        || offer.capabilities.max_chunk_bytes > MAX_CHUNK_BYTES
    {
        return Err("invalid_chunk_limit");
    }
    let sum = offer.files.iter().try_fold(0_u64, |acc, file| {
        acc.checked_add(file.size_bytes).ok_or("manifest_size_overflow")
    })?;
    if sum != offer.total_bytes {
        return Err("total_size_mismatch");
    }
    if offer.files.iter().any(|file| file.file_id.is_empty() || file.file_name.is_empty()) {
        return Err("invalid_file_metadata");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_offer() -> TransferOffer {
        TransferOffer {
            protocol_version: PROTOCOL_VERSION,
            transfer_id: "transfer-001".into(),
            sender: DeviceIdentity {
                device_id: "device-a".into(),
                display_name: "Office PC".into(),
                platform: DevicePlatform::Windows,
                certificate_fingerprint: "a".repeat(64),
                protocol_min: PROTOCOL_VERSION,
                protocol_max: PROTOCOL_VERSION,
            },
            capabilities: PeerCapabilities {
                max_chunk_bytes: 1024 * 1024,
                supports_resume: true,
                supports_folder_manifest: true,
            },
            files: vec![FileManifestEntry {
                file_id: "file-001".into(),
                file_name: "notes.txt".into(),
                size_bytes: 5,
                mime_type: Some("text/plain".into()),
                sha256: None,
                relative_path: None,
            }],
            total_bytes: 5,
        }
    }

    #[test]
    fn valid_offer_passes_manifest_validation() {
        assert_eq!(validate_offer(&sample_offer()), Ok(()));
    }

    #[test]
    fn mismatched_total_is_rejected() {
        let mut offer = sample_offer();
        offer.total_bytes = 6;
        assert_eq!(validate_offer(&offer), Err("total_size_mismatch"));
    }

    #[test]
    fn unsupported_version_is_rejected() {
        let mut offer = sample_offer();
        offer.protocol_version = 99;
        assert_eq!(validate_offer(&offer), Err("unsupported_protocol_version"));
    }

    #[test]
    fn control_message_round_trips_as_json() {
        let message = ControlMessage::Offer(sample_offer());
        let json = serde_json::to_string(&message).expect("serialize");
        let decoded: ControlMessage = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(decoded, message);
    }
}
