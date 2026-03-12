package com.malikhw.orbit.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val REPO = "MalikHw/orbit-screensaver-android"
    private const val API  = "https://api.github.com/repos/$REPO/releases/latest"
    private const val APK  = "orbit-screensaver.apk"

    data class ReleaseInfo(val tag: String, val downloadUrl: String)

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
            val json   = JSONObject(body)
            val tag    = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var apkUrl = ""
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
     * Downloads APK via plain HTTP stream into the app's private external
     * downloads dir (no storage permission needed), then fires the system
     * package installer. Pass [onProgress] to update UI (0–100).
     */
    suspend fun downloadAndInstall(
        context: Context,
        info: ReleaseInfo,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val outFile = File(context.getExternalFilesDir("downloads"), APK)

        val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout    = 60_000
        }
        val total = conn.contentLengthLong

        conn.inputStream.use { input ->
            outFile.outputStream().use { output ->
                val buf = ByteArray(8 * 1024)
                var downloaded = 0L
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) {
                        onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
        }
        conn.disconnect()

        withContext(Dispatchers.Main) {
            installApk(context, outFile)
        }
    }

    private fun installApk(context: Context, file: File) {
        if (!file.exists()) return
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
