package com.malikhw.orbit.dream

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // any touch wakes device (standard screensaver behaviour)
        isFullscreen  = true

        // Pass AssetManager once — it never changes
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

    // ── SurfaceHolder.Callback ────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        scope.launch {
            prepareBgAndCube()
            OrbitRenderer.nativeStart(holder.surface)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // EGL surface is tied to the window; size changes handled by recreating
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        OrbitRenderer.nativeSurfaceDestroyed()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    /**
     * Runs on IO then switches to main to call JNI (EGL must be touched
     * from the render thread, but bitmap uploads are thread-safe via mutex).
     */
    private suspend fun prepareBgAndCube() = withContext(Dispatchers.IO) {
        val p = OrbitPrefs(this@OrbitDreamService)

        // ── Background ────────────────────────────────────────────────────────
        val bgBitmap: Bitmap? = when (p.bgMode) {
            OrbitPrefs.BG_WALLPAPER, OrbitPrefs.BG_BLUR_WALLPAPER -> {
                getWallpaperBitmap(this@OrbitDreamService, p.bgMode == OrbitPrefs.BG_BLUR_WALLPAPER)
            }
            OrbitPrefs.BG_IMAGE -> {
                p.bgImageUri?.let { uri -> loadBitmapFromUri(uri) }
            }
            else -> null
        }
        bgBitmap?.let { OrbitRenderer.nativeSetBgBitmap(ensureArgb8888(it)) }

        // ── Cube ──────────────────────────────────────────────────────────────
        val cubeBitmap: Bitmap? = p.cubeImageUri?.let { uri ->
            loadBitmapFromUri(uri)?.let { bmp -> squareCrop(bmp) }
        }
        // if no custom cube, C++ will use the bundled asset — no need to push
        cubeBitmap?.let { OrbitRenderer.nativeSetCubeBitmap(ensureArgb8888(it)) }
    }

    private fun getWallpaperBitmap(ctx: Context, blur: Boolean): Bitmap? {
        return try {
            val wm = WallpaperManager.getInstance(ctx)
            val drawable = wm.drawable ?: return null
            val bmp = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(Canvas(b))
                b
            }
            if (blur) blurBitmap(bmp, 12) else bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun blurBitmap(src: Bitmap, radius: Int): Bitmap {
        val W = src.width; val H = src.height
        val pixels = IntArray(W * H)
        src.getPixels(pixels, 0, W, 0, 0, W, H)

        val rgba = ByteArray(W * H * 4)
        for (i in pixels.indices) {
            val c = pixels[i]
            rgba[i*4+0] = ((c shr 16) and 0xFF).toByte() // R
            rgba[i*4+1] = ((c shr  8) and 0xFF).toByte() // G
            rgba[i*4+2] = ( c         and 0xFF).toByte() // B
            rgba[i*4+3] = 0xFF.toByte()
        }

        // horizontal pass
        val tmp = ByteArray(W * H * 4)
        for (y in 0 until H) for (x in 0 until W) {
            var r=0; var g=0; var b=0; var cnt=0
            for (k in -radius..radius) {
                val nx = x + k
                if (nx < 0 || nx >= W) continue
                val idx = (y * W + nx) * 4
                r += rgba[idx].toInt() and 0xFF
                g += rgba[idx+1].toInt() and 0xFF
                b += rgba[idx+2].toInt() and 0xFF
                cnt++
            }
            val idx = (y * W + x) * 4
            tmp[idx]  = (r/cnt).toByte()
            tmp[idx+1]= (g/cnt).toByte()
            tmp[idx+2]= (b/cnt).toByte()
            tmp[idx+3]= 0xFF.toByte()
        }
        // vertical pass
        for (y in 0 until H) for (x in 0 until W) {
            var r=0; var g=0; var b=0; var cnt=0
            for (k in -radius..radius) {
                val ny = y + k
                if (ny < 0 || ny >= H) continue
                val idx = (ny * W + x) * 4
                r += tmp[idx].toInt() and 0xFF
                g += tmp[idx+1].toInt() and 0xFF
                b += tmp[idx+2].toInt() and 0xFF
                cnt++
            }
            val idx = (y * W + x) * 4
            rgba[idx]  = (r/cnt).toByte()
            rgba[idx+1]= (g/cnt).toByte()
            rgba[idx+2]= (b/cnt).toByte()
            rgba[idx+3]= 0xFF.toByte()
        }

        // back to Bitmap
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        for (i in 0 until W*H) {
            val r = rgba[i*4].toInt() and 0xFF
            val g = rgba[i*4+1].toInt() and 0xFF
            val b = rgba[i*4+2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setPixels(pixels, 0, W, 0, 0, W, H)
        return out
    }

    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) { null }
    }

    /** Force square by scaling to min(w,h) — auto-stretch for non-square images. */
    private fun squareCrop(bmp: Bitmap): Bitmap {
        val size = minOf(bmp.width, bmp.height)
        return Bitmap.createScaledBitmap(bmp, size, size, true)
    }

    private fun ensureArgb8888(bmp: Bitmap): Bitmap {
        return if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, false)
    }
}
