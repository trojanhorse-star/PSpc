package com.plazmas.android.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** LocalSend-inspired installed-app catalog using Android's launcher visibility. */
data class InstalledAppEntry(
    val label: String,
    val packageName: String,
    val versionName: String,
    val baseApk: File,
    val splitApkCount: Int
)
data class ExportedApk(val file: File, val contentUri: Uri, val app: InstalledAppEntry)

object InstalledAppCatalog {
    @Suppress("DEPRECATION")
    fun listLaunchableApps(context: Context): List<InstalledAppEntry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = mutableSetOf<String>()
        return pm.queryIntentActivities(launcherIntent, 0).mapNotNull { result ->
            val info = result.activityInfo.applicationInfo
            if (info.packageName == context.packageName || !seen.add(info.packageName)) return@mapNotNull null
            val apk = info.sourceDir?.let(::File) ?: return@mapNotNull null
            if (!apk.isFile || !apk.canRead()) return@mapNotNull null
            val version = runCatching { pm.getPackageInfo(info.packageName, 0).versionName ?: "Unknown" }.getOrDefault("Unknown")
            InstalledAppEntry(
                label = pm.getApplicationLabel(info).toString(),
                packageName = info.packageName,
                versionName = version,
                baseApk = apk,
                splitApkCount = info.splitSourceDirs?.size ?: 0
            )
        }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun exportBaseApk(context: Context, app: InstalledAppEntry): ExportedApk {
        require(app.baseApk.isFile && app.baseApk.canRead()) { "APK is no longer accessible" }
        val folder = File(context.cacheDir, "apk-exports").apply { mkdirs() }
        val safeLabel = TransferFileSafety.safeFileName(app.label)
        val destination = File(folder, "$" + "safeLabel-v" + app.versionName + ".apk")
        app.baseApk.copyTo(destination, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", destination)
        return ExportedApk(destination, uri, app)
    }
}
