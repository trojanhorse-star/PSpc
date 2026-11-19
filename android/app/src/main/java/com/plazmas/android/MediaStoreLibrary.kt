package com.plazmas.android

import android.Manifest
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
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
    onAddToTransfer: (PickedFile) -> Unit
) {
    val context = LocalContext.current
    var denied by remember(category) { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize()) {
        Text(category.uppercase(), color = Cyan, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text("Browse media already on this phone. Plazma S only reads items Android makes available in this library.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (!MediaStoreLibrary.hasPermission(context, category)) {
            Button(onClick = { permissionLauncher.launch(MediaStoreLibrary.requiredPermissions(category)) }) {
                Text(if (denied) "Try permission again" else "Allow access to $category")
            }
            if (denied) {
                Spacer(Modifier.height(8.dp))
                Text("Access was not granted. You can still use the optional file picker in Files.",
                    color = Violet, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${media.size} items", color = Cyan, modifier = Modifier.weight(1f))
                TextButton(onClick = onLoadMedia) { Text("Refresh") }
            }
            if (media.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No $category found")
                    Spacer(Modifier.height(6.dp))
                    Text("New items appear here automatically when you reopen or refresh this section.",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(media, key = { it.uri.toString() }) { item ->
                        val alreadySelected = selected.any { it.uri == item.uri }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF172A47)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            MediaStoreThumbnail(item, category)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, maxLines = 1)
                                Text(formatSize(item.size), color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onAddToTransfer(item) }, enabled = !alreadySelected) {
                                Text(if (alreadySelected) "Added" else "Add")
                            }
                        }
                    }
                }
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
            Icon(icon, contentDescription = null, tint = Cyan)
        }
    }
}
