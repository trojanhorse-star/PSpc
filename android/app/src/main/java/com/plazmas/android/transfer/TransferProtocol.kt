package com.plazmas.android.transfer

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** LocalSend-v2-inspired wire models; transport and TLS are separate milestones. */
data class DeviceInfoV2(
    val alias: String,
    val version: String = "2.0",
    val deviceModel: String? = null,
    val deviceType: String,
    val fingerprint: String,
    val port: Int,
    val protocol: String = "https",
    val download: Boolean = false
)
data class FileMetadataV2(
    val id: String,
    val fileName: String,
    val size: Long,
    val fileType: String,
    val sha256: String? = null
)
data class PrepareUploadV2(val info: DeviceInfoV2, val files: Map<String, FileMetadataV2>)
data class PreparedUploadV2(val sessionId: String, val files: Map<String, String>)
enum class TransferDirection { SEND, RECEIVE }
enum class TransferState { QUEUED, WAITING_FOR_APPROVAL, TRANSFERRING, VERIFYING, COMPLETE, FAILED, CANCELLED }

object TransferFileSafety {
    private val unsafeName = Regex("[^A-Za-z0-9._ -]")
    fun safeFileName(name: String): String {
        val cleaned = name.replace('\\', '/').substringAfterLast('/').replace(unsafeName, "_").trim()
        return cleaned.take(180).ifBlank { "received_file" }
    }
    fun sha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open selected file" }
        stream.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(256 * 1024).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
