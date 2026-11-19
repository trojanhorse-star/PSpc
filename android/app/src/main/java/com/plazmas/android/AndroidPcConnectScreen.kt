package com.plazmas.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plazmas.android.transfer.AndroidPcTransferClient
import com.plazmas.android.transfer.DiscoveredPc
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

private val ConnectCyan = Color(0xFF69E8FF)
private val ConnectPanel = Color(0xFF182A48)
private val ConnectMuted = Color(0xFF9AA8C4)

@Composable
fun AndroidPcConnectScreen(files: List<PickedFile>) {
    val context = LocalContext.current
    val client = remember(context) { AndroidPcTransferClient(context.applicationContext) }
    val peers = remember { mutableStateListOf<DiscoveredPc>() }
    val trustedFingerprints = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Find a Plazma S PC on the same Wi-Fi network.") }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    DisposableEffect(client) {
        onDispose { client.stopDiscovery() }
    }

    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF172846), Color(0xFF0B1427)))).padding(12.dp)) {
        Text("ANDROID → PC", color = ConnectCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Send selected files, photos, videos and audio to a Plazma S PC. APKs and app packages are not transferable.", color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    peers.clear()
                    scanning = true
                    status = "Searching your local network…"
                    client.discover(
                        onPeer = { peer ->
                            if (peers.none { it.host == peer.host && it.port == peer.port }) peers.add(peer)
                        },
                        onError = { status = it }
                    )
                },
                enabled = !scanning && !sending
            ) { Text(if (scanning) "Scanning…" else "Find PCs") }
            OutlinedButton(onClick = { client.stopDiscovery(); scanning = false }, enabled = scanning) { Text("Stop") }
        }
        Spacer(Modifier.height(12.dp))
        Text("SELECTED FILES: ${files.size}", color = ConnectCyan, style = MaterialTheme.typography.labelMedium)
        if (files.isEmpty()) {
            Text("Choose files or media in the Files or Media tab first.", color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(files.take(3).joinToString { it.name } + if (files.size > 3) " + ${files.size - 3} more" else "", color = ConnectMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Spacer(Modifier.height(12.dp))
        Text(status, color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
        progress?.let { (done, total) ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { if (total == 0L) 0f else done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("${done} / ${total} bytes", color = ConnectMuted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(peers, key = { "${it.host}:${it.port}" }) { peer ->
                var confirmed by remember(peer.fingerprint) { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), colors = CardDefaults.cardColors(containerColor = ConnectPanel)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(peer.serviceName, fontWeight = FontWeight.Bold)
                        Text("${peer.platform} · ${peer.host}:${peer.port}", color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Text("PC TLS fingerprint", color = ConnectCyan, style = MaterialTheme.typography.labelSmall)
                        Text(peer.fingerprint.chunked(4).joinToString(" "), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, enabled = !sending)
                            Text("I compared this fingerprint with the PC screen", color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                if (files.isEmpty()) {
                                    status = "Select at least one file or media item first."
                                } else {
                                    sending = true
                                    progress = 0L to files.sumOf { it.size }
                                    status = "Connecting securely to ${peer.serviceName}…"
                                    scope.launch {
                                        runCatching {
                                            client.sendFiles(peer, files, android.os.Build.MODEL) { done, total ->
                                                progress = done to total
                                            }
                                        }.onSuccess {
                                            status = "Transfer completed and the PC confirmed file verification."
                                        }.onFailure {
                                            status = it.message ?: "Transfer failed."
                                        }
                                        sending = false
                                    }
                                }
                            },
                            enabled = confirmed && files.isNotEmpty() && !sending,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (sending) "Sending…" else "Verify & send to this PC") }
                    }
                }
            }
        }
    }
}
