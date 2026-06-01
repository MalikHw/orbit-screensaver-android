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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullscreenPreviewActivity : ComponentActivity(), SurfaceHolder.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // real fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        val surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        setContentView(surfaceView)
        surfaceView.setOnClickListener { finish() }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        scope.launch {
            val p = OrbitPrefs(this@FullscreenPreviewActivity)
            OrbitRenderer.nativeSetAssetManager(assets)
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
            withContext(Dispatchers.IO) {
                // bg image
                if (p.bgMode == OrbitPrefs.BG_IMAGE && p.bgImageUri != null) {
                    loadBitmap(p.bgImageUri!!)?.let { bmp ->
                        OrbitRenderer.nativeSetBgBitmap(ensureArgb8888(bmp))
                    }
                }
                // cube image
                p.cubeImageUri?.let { uri ->
                    loadBitmap(uri)?.let { bmp ->
                        val size = minOf(bmp.width, bmp.height)
                        val cropped = Bitmap.createScaledBitmap(bmp, size, size, true)
                        OrbitRenderer.nativeSetCubeBitmap(ensureArgb8888(cropped))
                    }
                }
            }
            OrbitRenderer.nativeStart(holder.surface)
        }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) { OrbitRenderer.nativeSurfaceDestroyed() }
    override fun onDestroy() {
        super.onDestroy()
        OrbitRenderer.nativeStop()
        scope.cancel()
    }
    private fun loadBitmap(uriString: String): Bitmap? = try {
        val uri = Uri.parse(uriString)
        contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    } catch (_: Exception) { null }
    private fun ensureArgb8888(bmp: Bitmap): Bitmap =
        if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, false)
}