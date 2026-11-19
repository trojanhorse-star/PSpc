package com.plazmas.android.transfer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

const val PLAZMA_PROTOCOL_VERSION: Int = 1
const val PLAZMA_MAX_MANIFEST_FILES: Int = 10_000
const val PLAZMA_MAX_CHUNK_BYTES: Long = 4L * 1024L * 1024L

@Serializable
enum class DevicePlatform {
    @SerialName("windows") WINDOWS,
    @SerialName("android") ANDROID,
    @SerialName("linux") LINUX,
    @SerialName("macos") MACOS,
    @SerialName("other") OTHER
}

@Serializable
data class DeviceIdentity(
    val deviceId: String,
    val displayName: String,
    val platform: DevicePlatform,
    val certificateFingerprint: String,
    val protocolMin: Int,
    val protocolMax: Int
)

@Serializable
data class PeerCapabilities(
    val maxChunkBytes: Long,
    val supportsResume: Boolean,
    val supportsFolderManifest: Boolean
)

@Serializable
data class FileManifestEntry(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String? = null,
    val sha256: String? = null,
    val relativePath: String? = null
)

@Serializable
data class TransferOffer(
    val protocolVersion: Int,
    val transferId: String,
    val sender: DeviceIdentity,
    val capabilities: PeerCapabilities,
    val files: List<FileManifestEntry>,
    val totalBytes: Long
)

@Serializable
enum class TransferDecisionKind {
    @SerialName("accept") ACCEPT,
    @SerialName("reject") REJECT
}

@Serializable
data class TransferDecision(
    val transferId: String,
    val decision: TransferDecisionKind,
    val sessionToken: String? = null,
    val reasonCode: String? = null
)

@Serializable
data class ChunkRequest(
    val transferId: String,
    val fileId: String,
    val offsetBytes: Long,
    val lengthBytes: Long,
    val chunkSha256: String
)

@Serializable
data class ChunkAcknowledgement(
    val transferId: String,
    val fileId: String,
    val nextOffsetBytes: Long,
    val chunkSha256: String
)

@Serializable
enum class ProtocolTransferState {
    @SerialName("queued") QUEUED,
    @SerialName("awaiting_approval") AWAITING_APPROVAL,
    @SerialName("transferring") TRANSFERRING,
    @SerialName("verifying") VERIFYING,
    @SerialName("complete") COMPLETE,
    @SerialName("rejected") REJECTED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("failed") FAILED
}

sealed interface ControlMessage {
    data class Offer(val payload: TransferOffer) : ControlMessage
    data class Decision(val payload: TransferDecision) : ControlMessage
    data class ChunkRequestMessage(val payload: ChunkRequest) : ControlMessage
    data class ChunkAcknowledgementMessage(val payload: ChunkAcknowledgement) : ControlMessage
    data class Cancel(val transferId: String) : ControlMessage
    data class State(val transferId: String, val state: ProtocolTransferState) : ControlMessage
}

/**
 * JSON wire codec matching Rust serde exactly:
 * - camelCase DTO members
 * - snake_case enum values
 * - externally tagged enum envelope: {"type":"offer","payload":{...}}
 * - struct-variant payload members remain snake_case, as in the Rust enum
 * - nullable Option fields are emitted as explicit JSON nulls
 */
object PlazmaProtocolJson {
    val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        classDiscriminator = "type"
    }

    fun encode(message: ControlMessage): String {
        val envelope = when (message) {
            is ControlMessage.Offer -> envelope("offer", json.encodeToJsonElement(message.payload))
            is ControlMessage.Decision -> envelope("decision", json.encodeToJsonElement(message.payload))
            is ControlMessage.ChunkRequestMessage -> envelope("chunk_request", json.encodeToJsonElement(message.payload))
            is ControlMessage.ChunkAcknowledgementMessage -> envelope("chunk_acknowledgement", json.encodeToJsonElement(message.payload))
            is ControlMessage.Cancel -> envelope(
                "cancel",
                buildJsonObject { put("transfer_id", message.transferId) }
            )
            is ControlMessage.State -> envelope(
                "state",
                buildJsonObject {
                    put("transfer_id", message.transferId)
                    put("state", message.state.name.lowercase().replace("awaiting_approval", "awaiting_approval"))
                }
            )
        }
        return json.encodeToString(envelope)
    }

    fun decode(wireJson: String): ControlMessage {
        val envelope = json.decodeFromString<JsonObject>(wireJson)
        val type = envelope["type"]?.let { (it as? JsonPrimitive)?.content }
            ?: error("missing_control_message_type")
        val payload = envelope["payload"] ?: error("missing_control_message_payload")
        return when (type) {
            "offer" -> ControlMessage.Offer(json.decodeFromJsonElement<TransferOffer>(payload))
            "decision" -> ControlMessage.Decision(json.decodeFromJsonElement<TransferDecision>(payload))
            "chunk_request" -> ControlMessage.ChunkRequestMessage(json.decodeFromJsonElement<ChunkRequest>(payload))
            "chunk_acknowledgement" -> ControlMessage.ChunkAcknowledgementMessage(json.decodeFromJsonElement<ChunkAcknowledgement>(payload))
            "cancel" -> ControlMessage.Cancel(payload.jsonObjectString("transfer_id"))
            "state" -> ControlMessage.State(
                payload.jsonObjectString("transfer_id"),
                stateFromWire(payload.jsonObjectString("state"))
            )
            else -> error("unknown_control_message_type")
        }
    }

    private fun envelope(type: String, payload: kotlinx.serialization.json.JsonElement) =
        buildJsonObject {
            put("type", type)
            put("payload", payload)
        }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectString(key: String): String =
        (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.content }
            ?: error("missing_payload_field_$key")

    private fun stateFromWire(value: String): ProtocolTransferState =
        ProtocolTransferState.entries.firstOrNull {
            when (it) {
                ProtocolTransferState.AWAITING_APPROVAL -> "awaiting_approval"
                else -> it.name.lowercase()
            } == value
        } ?: error("unknown_transfer_state")
}

object TransferProtocolValidation {
    fun validateOffer(offer: TransferOffer): String? {
        if (offer.protocolVersion != PLAZMA_PROTOCOL_VERSION) return "unsupported_protocol_version"
        if (offer.transferId.isBlank()) return "invalid_transfer_id"
        if (offer.files.isEmpty() || offer.files.size > PLAZMA_MAX_MANIFEST_FILES) return "invalid_file_count"
        if (offer.capabilities.maxChunkBytes <= 0L || offer.capabilities.maxChunkBytes > PLAZMA_MAX_CHUNK_BYTES) return "invalid_chunk_limit"
        if (offer.totalBytes < 0L || offer.files.any { it.sizeBytes < 0L }) return "invalid_file_size"
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
