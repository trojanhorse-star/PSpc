package com.plazmas.android.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.provider.OpenableColumns
import com.plazmas.android.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.conscrypt.Conscrypt
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.Security
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/** A PC discovered on the local network. Discovery metadata is untrusted until its fingerprint is checked. */
data class DiscoveredPc(
    val serviceName: String,
    val host: String,
    val port: Int,
    val fingerprint: String,
    val platform: String
)

class AndroidPcTransferClient(private val context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discovery: NsdManager.DiscoveryListener? = null

    fun discover(onPeer: (DiscoveredPc) -> Unit, onError: (String) -> Unit) {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.trimEnd('.') != "_plazmas._tcp") return
                @Suppress("DEPRECATION")
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        onError("Could not resolve ${info.serviceName} (code $errorCode)")
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val attrs = info.attributes
                        val app = attrs["app"]?.toString(Charsets.UTF_8)
                        val version = attrs["protocolVersion"]?.toString(Charsets.UTF_8)
                        val tls = attrs["tls"]?.toString(Charsets.UTF_8)
                        val fingerprint = attrs["fingerprint"]?.toString(Charsets.UTF_8)?.lowercase().orEmpty()
                        if (app != "plazma-s" || version != "1" || tls != "1.3" ||
                            !fingerprint.matches(Regex("[0-9a-f]{64}"))) return
                        val host = info.host?.hostAddress ?: return
                        onPeer(DiscoveredPc(info.serviceName, host, info.port, fingerprint,
                            attrs["platform"]?.toString(Charsets.UTF_8) ?: "unknown"))
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsd.stopServiceDiscovery(this) }
                onError("LAN discovery could not start (code $errorCode)")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsd.stopServiceDiscovery(this) }
                onError("LAN discovery could not stop (code $errorCode)")
            }
        }
        discovery = listener
        nsd.discoverServices("_plazmas._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }

    suspend fun sendFiles(
        peer: DiscoveredPc,
        files: List<PickedFile>,
        displayName: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "Choose at least one file." }
        require(peer.fingerprint.matches(Regex("[0-9a-f]{64}"))) { "The PC certificate fingerprint is invalid." }
        val manifest = files.map { picked ->
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            context.contentResolver.openInputStream(picked.uri)?.use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    size += count
                }
            } ?: error("Cannot read ${picked.name}")
            FileManifestEntry(
                fileId = UUID.randomUUID().toString(),
                fileName = safeDisplayName(picked.name),
                sizeBytes = size,
                mimeType = picked.mime,
                sha256 = digest.digest().toHex()
            )
        }
        val totalBytes = manifest.sumOf { it.sizeBytes }
        val transferId = UUID.randomUUID().toString()
        val deviceId = context.getSharedPreferences("plazma_device", Context.MODE_PRIVATE)
            .getString("device_id", null) ?: UUID.randomUUID().toString().also {
                context.getSharedPreferences("plazma_device", Context.MODE_PRIVATE).edit().putString("device_id", it).apply()
            }
        val offer = TransferOffer(
            protocolVersion = PLAZMA_PROTOCOL_VERSION,
            transferId = transferId,
            sender = DeviceIdentity(
                deviceId = deviceId,
                displayName = displayName.ifBlank { "Plazma S Android" },
                platform = DevicePlatform.ANDROID,
                // The PC receiver currently does not authenticate Android client certificates.
                // Do not present this field as a verified Android TLS identity.
                certificateFingerprint = "",
                protocolMin = PLAZMA_PROTOCOL_VERSION,
                protocolMax = PLAZMA_PROTOCOL_VERSION
            ),
            capabilities = PeerCapabilities(
                maxChunkBytes = PLAZMA_MAX_CHUNK_BYTES,
                supportsResume = false,
                supportsFolderManifest = false
            ),
            files = manifest,
            totalBytes = totalBytes
        )
        val trustManager = PinnedPcTrustManager(peer.fingerprint)
        if (Security.getProvider(Conscrypt.PROVIDER_NAME) == null) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }
        val sslContext = SSLContext.getInstance("TLSv1.3", Conscrypt.PROVIDER_NAME)
        sslContext.init(null, arrayOf(trustManager), null)
        val socket = (sslContext.socketFactory.createSocket() as SSLSocket)
        socket.enabledProtocols = arrayOf("TLSv1.3")
        try {
            socket.connect(InetSocketAddress(peer.host, peer.port), 10_000)
            socket.soTimeout = 60_000
            socket.startHandshake()
            DataOutputStream(BufferedOutputStream(socket.outputStream)).use { output ->
                DataInputStream(BufferedInputStream(socket.inputStream)).use { input ->
                    writeControl(output, ControlMessage.Offer(offer))
                    val decision = readControl(input) as? ControlMessage.Decision
                        ?: error("PC returned an invalid transfer decision.")
                    require(decision.payload.transferId == transferId) { "PC transfer ID did not match." }
                    require(decision.payload.decision == TransferDecisionKind.ACCEPT) {
                        "The PC declined the transfer."
                    }
                    require(!decision.payload.sessionToken.isNullOrBlank()) { "PC did not issue a transfer session." }

                    var completed = 0L
                    files.forEachIndexed { index, picked ->
                        context.contentResolver.openInputStream(picked.uri)?.use { source ->
                            var offset = 0L
                            val buffer = ByteArray(1024 * 1024)
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                val bytes = buffer.copyOf(count)
                                val chunkHash = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
                                writeControl(output, ControlMessage.ChunkRequestMessage(
                                    ChunkRequest(transferId, manifest[index].fileId, offset, count.toLong(), chunkHash)
                                ))
                                output.write(bytes)
                                output.flush()
                                val ack = readControl(input) as? ControlMessage.ChunkAcknowledgementMessage
                                    ?: error("PC returned an invalid chunk acknowledgement.")
                                require(ack.payload.transferId == transferId &&
                                    ack.payload.fileId == manifest[index].fileId &&
                                    ack.payload.nextOffsetBytes == offset + count &&
                                    ack.payload.chunkSha256 == chunkHash) { "PC chunk verification failed." }
                                offset += count
                                completed += count
                                onProgress(completed, totalBytes)
                            }
                            require(offset == manifest[index].sizeBytes) { "Source file changed during transfer." }
                        } ?: error("Cannot reopen ${picked.name}")
                    }
                    val finalState = readControl(input) as? ControlMessage.State
                        ?: error("PC did not confirm transfer completion.")
                    require(finalState.transferId == transferId && finalState.state == ProtocolTransferState.COMPLETE) {
                        "PC did not confirm a verified transfer."
                    }
                }
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun writeControl(output: DataOutputStream, message: ControlMessage) {
        val bytes = PlazmaProtocolJson.encode(message).toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 1024 * 1024) { "Control message is too large." }
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun readControl(input: DataInputStream): ControlMessage {
        val size = input.readInt()
        require(size in 1..(1024 * 1024)) { "Invalid PC control frame." }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return PlazmaProtocolJson.decode(bytes.toString(Charsets.UTF_8))
    }

    private fun safeDisplayName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .map { if (it.isISOControl() || it in "<>:\"/\\|?*") '_' else it }
            .joinToString("").trim().trim('.').take(160).ifBlank { "shared_file" }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

private class PinnedPcTrustManager(private val expectedFingerprint: String) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw java.security.cert.CertificateException("Client certificates are not used by this sender.")
    }
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw java.security.cert.CertificateException("PC did not provide a certificate.")
        val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded).toHex()
        if (!MessageDigest.isEqual(actual.toByteArray(), expectedFingerprint.toByteArray())) {
            throw java.security.cert.CertificateException("PC certificate fingerprint does not match the discovered device.")
        }
    }
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
