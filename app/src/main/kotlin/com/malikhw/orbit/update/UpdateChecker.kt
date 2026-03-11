package com.malikhw.orbit.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val REPO  = "MalikHw/orbit-screensaver-android"
    private const val API   = "https://api.github.com/repos/$REPO/releases/latest"
    private const val APK   = "orbit-screensaver.apk"

    data class ReleaseInfo(val tag: String, val downloadUrl: String)

    /**
     * Returns the latest release info, or null on failure.
     * Call from a coroutine on Dispatchers.IO.
     */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "OrbitScreensaverAndroid")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8_000
                readTimeout    = 8_000
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json     = JSONObject(body)
            val tag      = json.getString("tag_name")
            val assets   = json.getJSONArray("assets")
            var apkUrl   = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == APK) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isEmpty()) null else ReleaseInfo(tag, apkUrl)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads the APK using DownloadManager (shows in notification bar)
     * and triggers the system installer when done.
     */
    fun downloadAndInstall(context: Context, info: ReleaseInfo) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Orbit Screensaver Update")
            .setDescription("Downloading ${info.tag}…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = dm.enqueue(request)

        // Listen for completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                ctx.unregisterReceiver(this)
                installApk(ctx)
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun installApk(context: Context) {
        val file = java.io.File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            APK
        )
        if (!file.exists()) return

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }

        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(install)
    }
}
