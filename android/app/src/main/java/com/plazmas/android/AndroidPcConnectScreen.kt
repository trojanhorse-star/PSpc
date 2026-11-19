package com.plazmas.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plazmas.android.transfer.AndroidPcTransferClient
import com.plazmas.android.transfer.DiscoveredPc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ConnectCyan = Color(0xFF69E8FF)
private val ConnectPanel = Color(0xFF182A48)
private val ConnectMuted = Color(0xFF9AA8C4)

@Composable
fun AndroidPcConnectScreen(files: List<PickedFile>) {
    val context = LocalContext.current
    val client = remember(context) { AndroidPcTransferClient(context.applicationContext) }
    val peers = remember { mutableStateListOf<DiscoveredPc>() }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var sendingTo by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Looking for Plazma S PCs on your Wi-Fi…") }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    val startScan: () -> Unit = {
        peers.clear()
        scanning = true
        status = "Searching for Plazma S PCs on this Wi-Fi…"
        runCatching {
            client.discover(
                onPeer = { peer ->
                    if (peers.none { it.host == peer.host && it.port == peer.port }) {
                        peers.add(peer)
                        status = "Found ${peers.size} nearby Plazma S PC${if (peers.size == 1) "" else "s"}. Tap one to connect."
                    }
                },
                onError = { error -> status = error }
            )
        }.onFailure { status = it.message ?: "Could not search this Wi-Fi network." }
    }

    LaunchedEffect(Unit) {
        startScan()
        delay(10_000)
        if (peers.isEmpty() && scanning) {
            status = "No PCs found yet. Check that both devices are on the same Wi-Fi and Plazma S is open on your PC."
        }
    }

    DisposableEffect(client) {
        onDispose { client.stopDiscovery() }
    }

    Column(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF172846), Color(0xFF0B1427))))
            .padding(12.dp)
    ) {
        Text("ANDROID → PC", color = ConnectCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your phone and PC just need to be on the same Wi-Fi. Plazma S finds nearby PCs automatically—tap one to send.",
            color = ConnectMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = startScan, enabled = sendingTo == null) {
                Text(if (scanning) "Refresh devices" else "Search again")
            }
            if (scanning) {
                Text("SEARCHING", color = ConnectCyan, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = ConnectPanel),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("READY TO SEND · ${files.size} FILES", color = ConnectCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (files.isEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text("Choose files or media in the Files or Media tab first.", color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Spacer(Modifier.height(5.dp))
                    Text(files.take(3).joinToString { it.name } + if (files.size > 3) " + ${files.size - 3} more" else "", color = ConnectMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(status, color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
        progress?.let { (done, total) ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total == 0L) 0f else done.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${formatConnectSize(done)} of ${formatConnectSize(total)}", color = ConnectMuted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        if (peers.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = ConnectPanel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⌁", color = ConnectCyan, style = MaterialTheme.typography.headlineLarge)
                    Text(if (scanning) "Finding your PC…" else "No devices found", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Keep Plazma S open on your PC and connect both devices to the same Wi-Fi network.", color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text("NEARBY DEVICES", color = ConnectCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(peers, key = { "${it.host}:${it.port}" }) { peer ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = ConnectPanel)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(peer.serviceName, fontWeight = FontWeight.Bold)
                                Text("${peer.platform} · Same Wi-Fi", color = ConnectMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    if (files.isEmpty()) {
                                        status = "Choose at least one file or media item first."
                                    } else {
                                        sendingTo = peer.serviceName
                                        progress = 0L to files.sumOf { it.size }
                                        status = "Connecting to ${peer.serviceName}… Approve the transfer on your PC when prompted."
                                        scope.launch {
                                            runCatching {
                                                client.sendFiles(peer, files, android.os.Build.MODEL) { done, total ->
                                                    progress = done to total
                                                }
                                            }.onSuccess {
                                                status = "Sent successfully. Your PC verified the received files."
                                            }.onFailure {
                                                status = it.message ?: "Could not send files. Check that both devices are on the same Wi-Fi."
                                            }
                                            sendingTo = null
                                        }
                                    }
                                },
                                enabled = sendingTo == null && files.isNotEmpty()
                            ) {
                                Text(if (sendingTo == peer.serviceName) "Sending…" else "Connect")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "SECURE CONNECTION · APPROVE ON THE RECEIVING PC · FILES & MEDIA ONLY",
            color = ConnectCyan, style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatConnectSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
