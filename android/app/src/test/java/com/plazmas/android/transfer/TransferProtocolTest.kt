package com.plazmas.android.transfer

import org.junit.Assert.assertEquals
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
        files = listOf(
            FileManifestEntry(
                fileId = "file-001",
                fileName = "notes.txt",
                sizeBytes = 5L,
                mimeType = "text/plain"
            )
        ),
        totalBytes = 5L
    )

    @Test fun validOfferPassesValidation() {
        assertEquals(null, TransferProtocolValidation.validateOffer(validOffer()))
    }

    @Test fun unsupportedVersionIsRejected() {
        assertEquals(
            "unsupported_protocol_version",
            TransferProtocolValidation.validateOffer(validOffer().copy(protocolVersion = 99))
        )
    }

    @Test fun mismatchedTotalIsRejected() {
        assertEquals(
            "total_size_mismatch",
            TransferProtocolValidation.validateOffer(validOffer().copy(totalBytes = 6L))
        )
    }

    @Test fun oversizedChunkLimitIsRejected() {
        val offer = validOffer().copy(
            capabilities = validOffer().capabilities.copy(maxChunkBytes = PLAZMA_MAX_CHUNK_BYTES + 1L)
        )
        assertEquals("invalid_chunk_limit", TransferProtocolValidation.validateOffer(offer))
    }
}
