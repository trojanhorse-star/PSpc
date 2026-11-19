package com.plazmas.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.Locale

private val Night = Color(0xFF060A16)
private val Panel = Color(0xFF14213A)
private val Cyan = Color(0xFF69E8FF)
private val Violet = Color(0xFFAA91FF)
private val Muted = Color(0xFF9AA8C4)

data class PickedFile(val name: String, val uri: Uri, val mime: String?, val size: Long)
data class ReceivedFile(val name: String, val size: Long, val path: String)

class MainActivity : ComponentActivity() {
    private val pickedFiles = mutableStateListOf<PickedFile>()
    private val receivedFiles = mutableStateListOf<ReceivedFile>()
    private val mediaFiles = mutableStateMapOf<String, List<PickedFile>>()
    private val installedApps = mutableStateListOf<com.plazmas.android.transfer.InstalledAppEntry>()

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val meta = readMetadata(uri)
            if (isAllowedTransferFile(meta) && pickedFiles.none { it.uri == uri }) pickedFiles.add(meta)
        }
    }

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val meta = readMetadata(uri)
            if (isAllowedTransferFile(meta) && pickedFiles.none { it.uri == uri }) pickedFiles.add(meta)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedIntent(intent)
        refreshReceived()
        refreshInstalledApps()
        setContent {
            PlazmaApp(
                pickedFiles = pickedFiles,
                receivedFiles = receivedFiles,
                mediaFiles = mediaFiles,
                installedApps = installedApps,
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onPickMedia = { mime -> mediaPicker.launch(arrayOf(mime)) },
                onLoadMedia = { category ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val items = runCatching { MediaStoreLibrary.query(this@MainActivity, category) }.getOrDefault(emptyList())
                        withContext(Dispatchers.Main) { mediaFiles[category] = items }
                    }
                },
                onAddMedia = { item ->
                    if (isAllowedTransferFile(item) && pickedFiles.none { it.uri == item.uri }) pickedFiles.add(item)
                },
                onRemovePicked = { pickedFiles.remove(it) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledApps()
    }

    private fun refreshInstalledApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = runCatching { com.plazmas.android.transfer.InstalledAppCatalog.listLaunchableApps(this@MainActivity) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                installedApps.clear()
                installedApps.addAll(apps)
            }
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
                if (!isAllowedTransferFile(meta)) return@runCatching
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
    mediaFiles: Map<String, List<PickedFile>>,
    installedApps: List<com.plazmas.android.transfer.InstalledAppEntry>,
    onPickFiles: () -> Unit,
    onPickMedia: (String) -> Unit,
    onLoadMedia: (String) -> Unit,
    onAddMedia: (PickedFile) -> Unit,
    onRemovePicked: (PickedFile) -> Unit
) {
    var page by remember { mutableStateOf("Home") }
    var drawerOpen by remember { mutableStateOf(false) }
    val screenOffset by animateDpAsState(if (drawerOpen) (-238).dp else 0.dp, label = "rightDrawerPush")
    val drawerOffset by animateDpAsState(if (drawerOpen) 0.dp else 250.dp, label = "rightDrawerSlide")
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, secondary = Violet, background = Night, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().offset(x = screenOffset),
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF101A30), contentColor = Muted, tonalElevation = 12.dp) {
                    NavigationBarItem(selected = page == "Files", onClick = { page = "Files"; drawerOpen = false },
                        icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = "Files") }, label = { Text("Files", fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0xFF16344C), unselectedIconColor = Muted))
                    NavigationBarItem(selected = page == "Images", onClick = { page = "Images"; onLoadMedia("Images"); drawerOpen = false },
                        icon = { Icon(Icons.Outlined.Image, contentDescription = "Images") }, label = { Text("Images", fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0xFF16344C), unselectedIconColor = Muted))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Surface(onClick = { page = "Home"; drawerOpen = false },
                            modifier = Modifier.size(58.dp).offset(y = (-9).dp).shadow(16.dp, CircleShape),
                            shape = CircleShape, color = Color(0xFF20D99A), contentColor = Color(0xFF061B18)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Home, contentDescription = "Home", modifier = Modifier.size(27.dp)) }
                        }
                    }
                    NavigationBarItem(selected = page == "Videos", onClick = { page = "Videos"; onLoadMedia("Videos"); drawerOpen = false },
                        icon = { Text("▶", color = if (page == "Videos") Cyan else Muted, fontSize = 18.sp) }, label = { Text("Videos", fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0xFF16344C), unselectedIconColor = Muted))
                    NavigationBarItem(selected = page == "Audio", onClick = { page = "Audio"; onLoadMedia("Audio"); drawerOpen = false },
                        icon = { Text("♫", color = if (page == "Audio") Cyan else Muted, fontSize = 19.sp) }, label = { Text("Audio", fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0xFF16344C), unselectedIconColor = Muted))
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101B34), Night, Color(0xFF080D1C)))).padding(padding).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).shadow(12.dp, RoundedCornerShape(15.dp)).clip(RoundedCornerShape(15.dp)).background(
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
                    IconButton(onClick = { drawerOpen = !drawerOpen }) {
                        Text(if (drawerOpen) "×" else "☰", color = Cyan, fontSize = 27.sp, fontWeight = FontWeight.Light)
                    }
                }
                Spacer(Modifier.height(22.dp))
                when (page) {
                    "Home" -> HomeScreen(pickedFiles.size, receivedFiles.size, installedApps, onPickFiles, { page = "Files" }, { page = "TransferModes" })
                    "Files" -> FilesScreen(pickedFiles, receivedFiles, onPickFiles, onRemovePicked)
                    "Media" -> MediaHubScreen(onImages = { page = "Images" }, onVideos = { page = "Videos" }, onAudio = { page = "Audio" })
                    "Images" -> MediaStoreScreen("Images", mediaFiles["Images"].orEmpty(), pickedFiles,
                        { onLoadMedia("Images") }, onAddMedia, onPickMedia)
                    "Videos" -> MediaStoreScreen("Videos", mediaFiles["Videos"].orEmpty(), pickedFiles,
                        { onLoadMedia("Videos") }, onAddMedia, onPickMedia)
                    "Audio" -> MediaStoreScreen("Audio", mediaFiles["Audio"].orEmpty(), pickedFiles,
                        { onLoadMedia("Audio") }, onAddMedia, onPickMedia)
                    "TransferModes" -> TransferModeChooser(
                        onAndroidToAndroid = { page = "AndroidToAndroid" },
                        onAndroidToPc = { page = "AndroidToPc" }
                    )
                    "AndroidToPc" -> AndroidPcConnectScreen(pickedFiles)
                    "AndroidToAndroid" -> AndroidToAndroidInfoScreen(onBack = { page = "TransferModes" })
                    "Activity" -> InfoScreen("Activity", "Your transfer and import history will appear here.")
                    else -> InfoScreen("Settings", "Choose your receive folder, theme, notifications and trusted devices here in a future update.")
                }
            }
        }
        if (drawerOpen) Box(Modifier.fillMaxSize().background(Color(0x66030A16)).clickable { drawerOpen = false })
        Column(
            Modifier.align(Alignment.CenterEnd).width(250.dp).fillMaxHeight().offset(x = drawerOffset)
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF20395E), Color(0xFF10182D), Color(0xFF080D1B))))
                .padding(20.dp)
        ) {
            Spacer(Modifier.height(26.dp))
            Text("PLAZMA MENU", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            ActionCard("Activity", "Transfers and import history", { page = "Activity"; drawerOpen = false }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ActionCard("Settings", "App preferences and trusted devices", { page = "Settings"; drawerOpen = false }, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Text("PLAZMA S · ANDROID", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(20.dp))
        }
        }
    }
}

@Composable
private fun HomeScreen(fileCount: Int, receivedCount: Int, installedApps: List<com.plazmas.android.transfer.InstalledAppEntry>, onPick: () -> Unit, onReceived: () -> Unit, onTransfer: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
        item {
            Box(Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF12617A), Color(0xFF293B79), Color(0xFF542B82), Color(0xFF172D50)))
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
            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onTransfer, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20D99A), contentColor = Color(0xFF061B18))) {
                        Text("↗  SEND", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                    Button(onClick = onReceived, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF275A91), contentColor = Color.White)) {
                        Text("↓  RECEIVE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("APPS ON THIS PHONE")
                Spacer(Modifier.weight(1f))
                Text(installedApps.size.toString(), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (installedApps.isEmpty()) {
            item { EmptyState("No launchable apps found", "Installed apps will appear here automatically when Android makes them visible.") }
        } else {
            items(installedApps.chunked(4), key = { row -> row.first().packageName }) { appRow ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                    appRow.forEach { app -> InstalledAppTile(app, Modifier.weight(1f)) }
                    repeat(4 - appRow.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().shadow(9.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF1C3151), Color(0xFF101B30)))).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, tint = Cyan)
                    Spacer(Modifier.width(9.dp))
                    Text("Device connection", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Send files to a nearby Plazma S PC over the same Wi-Fi network. Choose a nearby PC; secure identity checks happen automatically, and the receiving computer approves the transfer.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onTransfer, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A7892), contentColor = Color.White)) { Icon(Icons.Outlined.Wifi, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("Find a nearby PC", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(9.dp))
                Text("SAME WI-FI · ENCRYPTED CONNECTION · APPROVAL REQUIRED", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
            }
        }
    }
}

@Composable
private fun InstalledAppTile(app: com.plazmas.android.transfer.InstalledAppEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = modifier.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = "${app.label} app icon",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF17334F)), contentAlignment = Alignment.Center) {
                Text(app.label.take(1).uppercase(), color = Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(3.dp))
        Text(formatSize(app.sizeBytes), color = Muted, fontSize = 10.sp, maxLines = 1)
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
private fun MediaHubScreen(onImages: () -> Unit, onVideos: () -> Unit, onAudio: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            SectionTitle("YOUR MEDIA")
            Spacer(Modifier.height(8.dp))
            Text("Choose a dedicated library. Images, videos and audio stay in their own sections.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        item { ActionCard("Images", "Photos, pictures and image files", onImages, Modifier.fillMaxWidth()) }
        item { ActionCard("Videos", "Video clips and recordings", onVideos, Modifier.fillMaxWidth()) }
        item { ActionCard("Audio", "Music, songs and audio files", onAudio, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun MediaScreen(files: List<PickedFile>, title: String, mimePrefix: String, onPick: (String) -> Unit, onRemove: (PickedFile) -> Unit) {
    val media = files.filter { it.mime?.startsWith(mimePrefix) == true }
    Column(Modifier.fillMaxSize()) {
        SectionTitle(title.uppercase(Locale.getDefault()))
        Text("Your $title library. Select only $title from your phone using Android's secure system picker.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { onPick(mimePrefix) }) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text("Add $title") }
        Spacer(Modifier.height(18.dp))
        Text("${title.uppercase(Locale.getDefault())} · ${media.size}", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (media.isEmpty()) EmptyState("No $title selected yet", "Choose $title to add local items to this section.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(media, key = { it.uri.toString() }) { file -> FileRow(file.name, file.size, file.mime ?: title, onRemove = { onRemove(file) }) }
        }
    }
}

@Composable
private fun FileRow(name: String, size: Long, detail: String, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().shadow(7.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF1A2C49), Color(0xFF101B30)))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Column(modifier.shadow(10.dp, RoundedCornerShape(17.dp)).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF1D3556), Color(0xFF111D34)))).padding(16.dp)) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Cyan)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.shadow(10.dp, RoundedCornerShape(17.dp)), color = Color(0xFF182A48), shape = RoundedCornerShape(17.dp), tonalElevation = 6.dp) {
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

internal fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "Size unavailable"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}


/** Android packages are intentionally excluded from Plazma S Android transfer. */
internal fun isAllowedTransferFile(file: PickedFile): Boolean {
    val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val blockedExtensions = setOf("apk", "apks", "aab", "xapk")
    val blockedMimeTypes = setOf("application/vnd.android.package-archive", "application/x-apk")
    return extension !in blockedExtensions && file.mime?.lowercase(Locale.ROOT) !in blockedMimeTypes
}


@Composable
private fun TransferModeChooser(
    onAndroidToAndroid: () -> Unit,
    onAndroidToPc: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("CHOOSE TRANSFER MODE")
        Text("Select the device you want to transfer to.", color = Muted, fontSize = 12.sp)
        ActionCard("Android → Android", "Files, media, APKs and supported app packages", onAndroidToAndroid, Modifier.fillMaxWidth())
        ActionCard("Android → PC", "Files and media only · APK and app packages blocked", onAndroidToPc, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AndroidToAndroidInfoScreen(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Transfer modes", color = Cyan) }
        SectionTitle("ANDROID → ANDROID")
        Text("This route will support two-way files and media transfer, plus APK and supported app-package transfer between Android devices.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Text("App installation will use Android's supported package installer flow and will always require the receiving user's confirmation.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Text("The Android-to-Android transport and installation workflow are not implemented yet.", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
