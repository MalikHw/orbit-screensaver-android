package com.malikhw.orbit.settings

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.malikhw.orbit.dream.OrbitRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullscreenPreviewActivity : ComponentActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Real fullscreen logic
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        OrbitRenderer.nativeSetAssetManager(assets)

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        setContentView(surfaceView)

        surfaceView.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        pushSettingsToNative()
    }

    override fun onStop() {
        super.onStop()
        OrbitRenderer.nativeStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        scope.launch {
            delay(500) // 500ms delay before JNI starts
            prepareBgAndCube()
            OrbitRenderer.nativeStart(holder.surface)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        OrbitRenderer.nativeSurfaceDestroyed()
    }


    private fun pushSettingsToNative() {
        val p = OrbitPrefs(this)
        OrbitRenderer.nativeSetSettings(
            speed      = p.speed,
            fps        = p.fps,
            bgMode     = p.bgMode,
            bgR        = p.bgColorR,
            bgG        = p.bgColorG,
            bgB        = p.bgColorB,
            noGround   = p.noGround,
            orbScale   = p.orbScale,
            orbCount   = p.orbCount,
            cubeChance = p.cubeChance
        )
    }

    private suspend fun prepareBgAndCube() = withContext(Dispatchers.IO) {
        val p = OrbitPrefs(this@FullscreenPreviewActivity)

        val bgBitmap: Bitmap? = when (p.bgMode) {
            OrbitPrefs.BG_IMAGE -> p.bgImageUri?.let { loadBitmapFromUri(it) }
            else -> null
        }
        bgBitmap?.let { OrbitRenderer.nativeSetBgBitmap(ensureArgb8888(it)) }

        val cubeBitmap: Bitmap? = p.cubeImageUri?.let { uri ->
            loadBitmapFromUri(uri)?.let { squareCrop(it) }
        }
        cubeBitmap?.let { OrbitRenderer.nativeSetCubeBitmap(ensureArgb8888(it)) }
    }

    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) { null }
    }

    private fun squareCrop(bmp: Bitmap): Bitmap {
        val size = minOf(bmp.width, bmp.height)
        return Bitmap.createScaledBitmap(bmp, size, size, true)
    }

    private fun ensureArgb8888(bmp: Bitmap): Bitmap {
        return if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, false)
    }
}
