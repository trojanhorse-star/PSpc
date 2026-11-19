package com.plazmas.android.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferProtocolTest {
    private fun validOffer() = TransferOffer(
        protocolVersion = PLAZMA_PROTOCOL_VERSION,
        transferId = "transfer-001",
        sender = DeviceIdentity(
            deviceId = "device-a",
            displayName = "Android phone",
            platform = DevicePlatform.ANDROID,
            certificateFingerprint = "a".repeat(64),
            protocolMin = 1,
            protocolMax = 1
        ),
        capabilities = PeerCapabilities(
            maxChunkBytes = 1024L * 1024L,
            supportsResume = true,
            supportsFolderManifest = true
        ),
        files = listOf(FileManifestEntry("file-001", "notes.txt", 5L, "text/plain")),
        totalBytes = 5L
    )

    @Test fun validOfferPassesValidation() {
        assertEquals(null, TransferProtocolValidation.validateOffer(validOffer()))
    }

    @Test fun unsupportedVersionIsRejected() {
        assertEquals("unsupported_protocol_version", TransferProtocolValidation.validateOffer(validOffer().copy(protocolVersion = 99)))
    }

    @Test fun mismatchedTotalIsRejected() {
        assertEquals("total_size_mismatch", TransferProtocolValidation.validateOffer(validOffer().copy(totalBytes = 6L)))
    }

    @Test fun oversizedChunkLimitIsRejected() {
        val offer = validOffer().copy(capabilities = validOffer().capabilities.copy(maxChunkBytes = PLAZMA_MAX_CHUNK_BYTES + 1L))
        assertEquals("invalid_chunk_limit", TransferProtocolValidation.validateOffer(offer))
    }

    @Test fun offerWireFormatMatchesRustSerdeNamesAndEnums() {
        val wire = PlazmaProtocolJson.encode(ControlMessage.Offer(validOffer()))
        assertTrue(wire.contains("\"type\":\"offer\""))
        assertTrue(wire.contains("\"platform\":\"android\""))
        assertTrue(wire.contains("\"deviceId\":\"device-a\""))
        assertTrue(wire.contains("\"sizeBytes\":5"))
        assertTrue(wire.contains("\"relativePath\":null"))
        assertEquals(ControlMessage.Offer(validOffer()), PlazmaProtocolJson.decode(wire))
    }

    @Test fun stateControlUsesRustStructVariantSnakeCaseFields() {
        val wire = PlazmaProtocolJson.encode(ControlMessage.State("transfer-001", ProtocolTransferState.AWAITING_APPROVAL))
        assertTrue(wire.contains("\"transfer_id\":\"transfer-001\""))
        assertTrue(wire.contains("\"state\":\"awaiting_approval\""))
        assertEquals(ControlMessage.State("transfer-001", ProtocolTransferState.AWAITING_APPROVAL), PlazmaProtocolJson.decode(wire))
    }

    @Test fun cancelControlUsesRustStructVariantSnakeCaseFields() {
        val wire = PlazmaProtocolJson.encode(ControlMessage.Cancel("transfer-001"))
        assertTrue(wire.contains("\"transfer_id\":\"transfer-001\""))
        assertEquals(ControlMessage.Cancel("transfer-001"), PlazmaProtocolJson.decode(wire))
    }
}
