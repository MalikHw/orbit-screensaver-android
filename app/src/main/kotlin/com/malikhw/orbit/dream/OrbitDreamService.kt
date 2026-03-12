package com.malikhw.orbit.dream

import android.graphics.Bitmap
import android.net.Uri
import android.service.dreams.DreamService
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.malikhw.orbit.settings.OrbitPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrbitDreamService : DreamService(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen  = true

        OrbitRenderer.nativeSetAssetManager(assets)

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        setContentView(surfaceView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        pushSettingsToNative()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        OrbitRenderer.nativeStop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        scope.launch {
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
            cubeChance = p.cubeChance,
            gravityDir = p.gravityDir
        )
    }

    private suspend fun prepareBgAndCube() = withContext(Dispatchers.IO) {
        val p = OrbitPrefs(this@OrbitDreamService)

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

    private fun ensureArgb8888(bmp: Bitmap): Bitmap =
        if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, false)
}
