package com.plazmas.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

private val Night = Color(0xFF070B18)
private val Panel = Color(0xFF111A30)
private val Cyan = Color(0xFF19D8FF)
private val Violet = Color(0xFFB34BFF)
private val Muted = Color(0xFF9AA8C4)

data class PickedFile(val name: String, val uri: Uri, val mime: String?, val size: Long)
data class ReceivedFile(val name: String, val size: Long, val path: String)

class MainActivity : ComponentActivity() {
    private val pickedFiles = mutableStateListOf<PickedFile>()
    private val receivedFiles = mutableStateListOf<ReceivedFile>()

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val meta = readMetadata(uri)
            if (pickedFiles.none { it.uri == uri }) pickedFiles.add(meta)
        }
    }

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val meta = readMetadata(uri)
            if (pickedFiles.none { it.uri == uri }) pickedFiles.add(meta)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedIntent(intent)
        refreshReceived()
        setContent {
            PlazmaApp(
                pickedFiles = pickedFiles,
                receivedFiles = receivedFiles,
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onPickMedia = { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*")) },
                onRemovePicked = { pickedFiles.remove(it) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedIntent(intent)
        refreshReceived()
    }

    private fun readMetadata(uri: Uri): PickedFile {
        var name = "Shared file"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return PickedFile(name, uri, contentResolver.getType(uri), size)
    }

    private fun receiveSharedIntent(intent: Intent?) {
        if (intent == null) return
        val incoming = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (incoming.isEmpty()) return
        val folder = File(filesDir, "received").apply { mkdirs() }
        incoming.forEach { uri ->
            runCatching {
                val meta = readMetadata(uri)
                val safeName = meta.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120).ifBlank { "received_file" }
                var target = File(folder, safeName)
                if (target.exists()) target = File(folder, "${System.currentTimeMillis()}_$safeName")
                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            }
        }
    }

    private fun refreshReceived() {
        val folder = File(filesDir, "received")
        receivedFiles.clear()
        receivedFiles.addAll(folder.listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.lastModified() }.map {
            ReceivedFile(it.name, it.length(), it.absolutePath)
        })
    }
}

@Composable
private fun PlazmaApp(
    pickedFiles: List<PickedFile>,
    receivedFiles: List<ReceivedFile>,
    onPickFiles: () -> Unit,
    onPickMedia: () -> Unit,
    onRemovePicked: (PickedFile) -> Unit
) {
    var page by remember { mutableStateOf("Home") }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, secondary = Violet, background = Night, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0C1325), contentColor = Muted) {
                    val tabs = listOf(
                        Triple("Home", Icons.Outlined.Home, "Home"),
                        Triple("Files", Icons.Outlined.FolderOpen, "Files"),
                        Triple("Media", Icons.Outlined.Image, "Media"),
                        Triple("Connect", Icons.Outlined.Wifi, "Connect"),
                        Triple("Activity", Icons.Outlined.History, "Activity"),
                        Triple("Settings", Icons.Outlined.Settings, "Settings")
                    )
                    tabs.forEach { (label, icon, key) ->
                        NavigationBarItem(
                            selected = page == key,
                            onClick = { page = key },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan, selectedTextColor = Cyan,
                                indicatorColor = Color(0xFF16344C), unselectedIconColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(
                        Brush.linearGradient(listOf(Cyan, Color(0xFF285BFF), Violet))
                    ), contentAlignment = Alignment.Center) {
                        Text("P↔", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("Plazma S", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("ANDROID", fontSize = 9.sp, color = Cyan, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.Wifi, contentDescription = "Local transfer", tint = Muted)
                }
                Spacer(Modifier.height(22.dp))
                when (page) {
                    "Home" -> HomeScreen(pickedFiles.size, receivedFiles.size, onPickFiles, onPickMedia, { page = "Files" })
                    "Files" -> FilesScreen(pickedFiles, receivedFiles, onPickFiles, onRemovePicked)
                    "Media" -> MediaScreen(pickedFiles, onPickMedia, onRemovePicked)
                    "Connect" -> AndroidPcConnectScreen(pickedFiles)
                    "Activity" -> InfoScreen("Activity", "Your transfer and import history will appear here.")
                    else -> InfoScreen("Settings", "Choose your receive folder, theme, notifications and trusted devices here in a future update.")
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(fileCount: Int, receivedCount: Int, onPick: () -> Unit, onMedia: () -> Unit, onReceived: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF073D65), Color(0xFF24205B), Color(0xFF40175A)))
            ).padding(22.dp)) {
                Column {
                    Text("YOUR PERSONAL TRANSFER SPACE", color = Color(0xFFB5EFFF), fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(15.dp))
                    Text("Files move.\nFreely.", fontSize = 35.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(9.dp))
                    Text("A clean home for the files and media you share between your devices.", color = Color(0xFFD2D9F2), fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onPick, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Night)) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose files", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Selected", fileCount.toString(), Modifier.weight(1f))
                StatCard("Received", receivedCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("QUICK ACTIONS")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Browse media", "Photos, video & audio", onMedia, Modifier.weight(1f))
                ActionCard("Received files", "Open your inbox", onReceived, Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, tint = Cyan)
                    Spacer(Modifier.width(9.dp))
                    Text("Device connection", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("PC and Android device discovery is the next connection milestone. Your files stay on this phone until a transfer connection is available.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Text("NO CLOUD UPLOAD · NO APP MIGRATION", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun FilesScreen(files: List<PickedFile>, received: List<ReceivedFile>, onPick: () -> Unit, onRemove: (PickedFile) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SectionTitle("FILE PICKER")
        Text("Choose files using Android's secure system picker. Selected items are kept as local references; they are not uploaded.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onPick) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Add files") }
        Spacer(Modifier.height(18.dp))
        Text("SELECTED FILES · ${files.size}", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(files, key = { it.uri.toString() }) { file ->
                FileRow(file.name, file.size, file.mime ?: "File", onRemove = { onRemove(file) })
            }
            item { Spacer(Modifier.height(12.dp)); Text("RECEIVED IN APP INBOX · ${received.size}", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            items(received, key = { it.path }) { file -> FileRow(file.name, file.size, "Received locally") }
        }
    }
}

@Composable
private fun MediaScreen(files: List<PickedFile>, onPick: () -> Unit, onRemove: (PickedFile) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SectionTitle("YOUR MEDIA")
        Text("Select photos, videos and audio from your phone. Android controls which files you grant access to.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onPick) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text("Choose media") }
        Spacer(Modifier.height(18.dp))
        val media = files.filter { it.mime?.startsWith("image/") == true || it.mime?.startsWith("video/") == true || it.mime?.startsWith("audio/") == true }
        if (media.isEmpty()) EmptyState("No media selected yet", "Choose media to add local items to this view.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(media, key = { it.uri.toString() }) { FileRow(it.name, it.size, it.mime ?: "Media", onRemove = { onRemove(it) }) }
        }
    }
}

@Composable
private fun FileRow(name: String, size: Long, detail: String, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF172B49)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FolderOpen, null, tint = Cyan)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            Text("$detail · ${formatSize(size)}", color = Muted, fontSize = 10.sp)
        }
        if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove", color = Violet, fontSize = 11.sp) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(17.dp)).background(Panel).padding(16.dp)) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Cyan)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, color = Panel, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Cyan, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 35.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun InfoScreen(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        SectionTitle(title.uppercase(Locale.getDefault()))
        Spacer(Modifier.height(12.dp))
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "Size unavailable"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
