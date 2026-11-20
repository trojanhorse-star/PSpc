package com.plazmas.android

import android.Manifest
import android.content.Intent
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads Android shared-media collections only; no broad all-files access or private app folders. */
object MediaStoreLibrary {
    fun requiredPermissions(category: String): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 && category != "Audio" -> arrayOf(
            if (category == "Images") Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            when (category) {
                "Images" -> Manifest.permission.READ_MEDIA_IMAGES
                "Videos" -> Manifest.permission.READ_MEDIA_VIDEO
                else -> Manifest.permission.READ_MEDIA_AUDIO
            }
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPermission(context: Context, category: String): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 34 && category != "Audio") {
            val specific = if (category == "Images") Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_MEDIA_VIDEO
            return granted(specific) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val permission = when (category) {
                "Images" -> Manifest.permission.READ_MEDIA_IMAGES
                "Videos" -> Manifest.permission.READ_MEDIA_VIDEO
                else -> Manifest.permission.READ_MEDIA_AUDIO
            }
            return granted(permission)
        }
        return granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun query(context: Context, category: String): List<PickedFile> {
        val collection = when (category) {
            "Images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "Videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val result = mutableListOf<PickedFile>()
        context.contentResolver.query(collection, projection, null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Media item"
                val size = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn)
                val mime = if (mimeColumn >= 0 && !cursor.isNull(mimeColumn)) cursor.getString(mimeColumn) else null
                val uri = ContentUris.withAppendedId(collection, id)
                val file = PickedFile(name, uri, mime, size)
                if (isAllowedTransferFile(file)) result.add(file)
            }
        }
        return result
    }
}

@Composable
fun MediaStoreScreen(
    category: String,
    media: List<PickedFile>,
    selected: List<PickedFile>,
    onLoadMedia: () -> Unit,
    onToggleSelection: (PickedFile) -> Unit,
    onPickMedia: (String) -> Unit
) {
    val context = LocalContext.current
    var denied by remember(category) { mutableStateOf(false) }
    var search by remember(category) { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (MediaStoreLibrary.hasPermission(context, category)) {
            denied = false
            onLoadMedia()
        } else denied = true
    }
    LaunchedEffect(category) {
        if (MediaStoreLibrary.hasPermission(context, category)) onLoadMedia()
    }
    val filtered = remember(media, search) {
        media.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }
    val selectedUris = selected.map { it.uri }.toSet()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ON THIS DEVICE · ${category.uppercase()}", color = Color(0xFFB9D9F3),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onLoadMedia) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color(0xFF69E8FF))
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search device ${if (category == "Audio") "music" else category.lowercase()}") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF69E8FF),
                unfocusedBorderColor = Color(0xFF617B9A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF69E8FF)
            )
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { filtered.filter { it.uri !in selectedUris }.forEach(onToggleSelection) }) {
                Text("Select all", color = Color(0xFF70CFFF))
            }
            TextButton(onClick = { selected.filter { item -> media.any { it.uri == item.uri } }.forEach(onToggleSelection) }) {
                Text("Clear selection", color = Color(0xFF70CFFF))
            }
            Spacer(Modifier.weight(1f))
            Text("${filtered.count { it.uri in selectedUris }} selected", color = Color(0xFF9AA8C4),
                style = MaterialTheme.typography.labelSmall)
        }
        Text("Showing ${filtered.size} of ${media.size}", color = Color(0xFFB9D9F3),
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 7.dp))

        if (!MediaStoreLibrary.hasPermission(context, category)) {
            Button(onClick = { permissionLauncher.launch(MediaStoreLibrary.requiredPermissions(category)) }) {
                Text(if (denied) "Try permission again" else "Allow access to $category")
            }
            if (denied) {
                Spacer(Modifier.height(8.dp))
                Text("Access was not granted. You can still use the optional file picker in Files.",
                    color = Color(0xFFAA91FF), style = MaterialTheme.typography.bodySmall)
            }
        } else if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (media.isEmpty()) "No $category found" else "No matching items", color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Your device library updates when you refresh this section.",
                    color = Color(0xFF9AA8C4), style = MaterialTheme.typography.bodySmall)
            }
        } else if (category == "Audio") {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(filtered, key = { it.uri.toString() }) { item ->
                    val isSelected = item.uri in selectedUris
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF172A47)).padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF263D77)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color(0xFFB4C4FF))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, maxLines = 1)
                            Text("Unknown artist · ${formatSize(item.size)}", color = Color(0xFF9AA8C4),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,
                                    item.mime ?: "audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "Play audio", tint = Color(0xFFE3E9FF))
                        }
                        SelectionCircle(isSelected) { onToggleSelection(item) }
                        IconButton(onClick = { }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = Color(0xFF9AA8C4))
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                gridItems(filtered, key = { it.uri.toString() }) { item ->
                    val isSelected = item.uri in selectedUris
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF203A5D))) {
                        MediaStoreThumbnail(item, category)
                        if (category == "Videos") {
                            Box(Modifier.align(Alignment.Center).size(42.dp).clip(CircleShape)
                                .background(Color(0xEFFFFFFF)), contentAlignment = Alignment.Center) {
                                Text("▶", color = Color(0xFF111827), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        SelectionCircle(isSelected, Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                            onToggleSelection(item)
                        }
                        Box(Modifier.matchParentSize().clickable {
                            if (category == "Videos") {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,
                                        item.mime ?: "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                            } else onToggleSelection(item)
                        })
                    }
                }
            }
        }
        TextButton(onClick = {
            val mime = when (category) {
                "Images" -> "image/*"
                "Videos" -> "video/*"
                else -> "audio/*"
            }
            onPickMedia(mime)
        }, modifier = Modifier.align(Alignment.End)) { Text("Choose files instead", color = Color(0xFF69E8FF)) }
    }
}

@Composable
private fun SelectionCircle(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(28.dp).clip(CircleShape)
        .background(if (selected) Color(0xFF087C62) else Color(0xB907101C))
        .clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (selected) Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
        else Box(Modifier.size(17.dp).clip(CircleShape).background(Color.Transparent)
            .then(Modifier.padding(1.dp))) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun MediaStoreThumbnail(item: PickedFile, category: String) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        if (category != "Audio" && Build.VERSION.SDK_INT >= 29) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(item.uri, Size(144, 144), null) }.getOrNull()
            }
        }
    }
    Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF203A5D)),
        contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = item.name, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        } else {
            val icon = when (category) {
                "Images" -> Icons.Outlined.Image
                "Videos" -> Icons.Outlined.VideoLibrary
                else -> Icons.Outlined.MusicNote
            }
            Icon(icon, contentDescription = null, tint = Color(0xFF69E8FF))
        }
    }
}
