package com.plazmas.android.transfer

/**
 * Kotlin mirror of the platform-neutral Plazma S transfer protocol v1 DTOs.
 *
 * Keep property names camelCase and enum wire values snake_case when adding
 * the JSON codec. Network transport must use authenticated TLS 1.3; these
 * models do not establish trust or perform encryption.
 */
const val PLAZMA_PROTOCOL_VERSION: Int = 1
const val PLAZMA_MAX_MANIFEST_FILES: Int = 10_000
const val PLAZMA_MAX_CHUNK_BYTES: Long = 4L * 1024L * 1024L

enum class DevicePlatform { WINDOWS, ANDROID, LINUX, MACOS, OTHER }

data class DeviceIdentity(
    val deviceId: String,
    val displayName: String,
    val platform: DevicePlatform,
    val certificateFingerprint: String,
    val protocolMin: Int,
    val protocolMax: Int
)

data class PeerCapabilities(
    val maxChunkBytes: Long,
    val supportsResume: Boolean,
    val supportsFolderManifest: Boolean
)

data class FileManifestEntry(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String? = null,
    val sha256: String? = null,
    val relativePath: String? = null
)

data class TransferOffer(
    val protocolVersion: Int,
    val transferId: String,
    val sender: DeviceIdentity,
    val capabilities: PeerCapabilities,
    val files: List<FileManifestEntry>,
    val totalBytes: Long
)

enum class TransferDecisionKind { ACCEPT, REJECT }

data class TransferDecision(
    val transferId: String,
    val decision: TransferDecisionKind,
    val sessionToken: String? = null,
    val reasonCode: String? = null
)

data class ChunkRequest(
    val transferId: String,
    val fileId: String,
    val offsetBytes: Long,
    val lengthBytes: Long,
    val chunkSha256: String
)

data class ChunkAcknowledgement(
    val transferId: String,
    val fileId: String,
    val nextOffsetBytes: Long,
    val chunkSha256: String
)

enum class ProtocolTransferState {
    QUEUED, AWAITING_APPROVAL, TRANSFERRING, VERIFYING,
    COMPLETE, REJECTED, CANCELLED, FAILED
}

sealed interface ControlMessage {
    data class Offer(val payload: TransferOffer) : ControlMessage
    data class Decision(val payload: TransferDecision) : ControlMessage
    data class ChunkRequestMessage(val payload: ChunkRequest) : ControlMessage
    data class ChunkAcknowledgementMessage(val payload: ChunkAcknowledgement) : ControlMessage
    data class Cancel(val transferId: String) : ControlMessage
    data class State(val transferId: String, val state: ProtocolTransferState) : ControlMessage
}

object TransferProtocolValidation {
    fun validateOffer(offer: TransferOffer): String? {
        if (offer.protocolVersion != PLAZMA_PROTOCOL_VERSION) return "unsupported_protocol_version"
        if (offer.transferId.isBlank()) return "invalid_transfer_id"
        if (offer.files.isEmpty() || offer.files.size > PLAZMA_MAX_MANIFEST_FILES) {
            return "invalid_file_count"
        }
        if (offer.capabilities.maxChunkBytes <= 0L ||
            offer.capabilities.maxChunkBytes > PLAZMA_MAX_CHUNK_BYTES
        ) return "invalid_chunk_limit"
        if (offer.totalBytes < 0L || offer.files.any { it.sizeBytes < 0L }) {
            return "invalid_file_size"
        }
        var total = 0L
        for (file in offer.files) {
            if (file.fileId.isBlank() || file.fileName.isBlank()) return "invalid_file_metadata"
            if (Long.MAX_VALUE - total < file.sizeBytes) return "manifest_size_overflow"
            total += file.sizeBytes
        }
        if (total != offer.totalBytes) return "total_size_mismatch"
        return null
    }
}
