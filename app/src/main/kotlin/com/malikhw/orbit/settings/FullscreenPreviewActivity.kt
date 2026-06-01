package com.malikhw.orbit.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
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

class FullscreenPreviewActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var surfaceView: SurfaceView? = null
    private var currentHolder: SurfaceHolder? = null
    companion object {
        private const val TAG = "FullscreenPreview"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        // real fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        surfaceView = SurfaceView(this).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Log.d(TAG, "surfaceCreated")
                    currentHolder = holder
                    startRenderer(holder)
                }
                
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    Log.d(TAG, "surfaceChanged: $width x $height")
                }
                
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d(TAG, "surfaceDestroyed")
                    stopRenderer()
                    currentHolder = null
                }
            })
        }
        
        setContentView(surfaceView)
        surfaceView?.setOnClickListener { 
            Log.d(TAG, "Clicked - finishing")
            finish() 
        }
    }
    
    private fun startRenderer(holder: SurfaceHolder) {
        scope.launch {
            try {
                Log.d(TAG, "Starting renderer")
                val p = OrbitPrefs(this@FullscreenPreviewActivity)
                OrbitRenderer.nativeSetAssetManager(assets)
                OrbitRenderer.nativeSetSettings(
                    speed = p.speed,
                    fps = p.fps,
                    bgMode = p.bgMode,
                    bgR = p.bgColorR,
                    bgG = p.bgColorG,
                    bgB = p.bgColorB,
                    noGround = p.noGround,
                    orbScale = p.orbScale,
                    orbCount = p.orbCount,
                    cubeChance = p.cubeChance
                )
                withContext(Dispatchers.IO) {
                    if (p.bgMode == OrbitPrefs.BG_IMAGE && p.bgImageUri != null) {
                        try {
                            val uri = Uri.parse(p.bgImageUri)
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                bmp?.let {
                                    val argb = if (it.config == Bitmap.Config.ARGB_8888) it
                                              else it.copy(Bitmap.Config.ARGB_8888, false)
                                    OrbitRenderer.nativeSetBgBitmap(argb)
                                    Log.d(TAG, "Background image loaded")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load background", e)
                        }
                    }
                    if (p.cubeImageUri != null) {
                        try {
                            val uri = Uri.parse(p.cubeImageUri)
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                bmp?.let {
                                    val size = minOf(it.width, it.height)
                                    val cropped = Bitmap.createScaledBitmap(it, size, size, true)
                                    val argb = if (cropped.config == Bitmap.Config.ARGB_8888) cropped
                                              else cropped.copy(Bitmap.Config.ARGB_8888, false)
                                    OrbitRenderer.nativeSetCubeBitmap(argb)
                                    Log.d(TAG, "Cube image loaded")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load cube image", e)
                        }
                    }
                }
                OrbitRenderer.nativeStart(holder.surface)
                Log.d(TAG, "Renderer started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start renderer", e)
            }
        }
    }
    private fun stopRenderer() {
        try {
            OrbitRenderer.nativeSurfaceDestroyed()
            OrbitRenderer.nativeStop()
            Log.d(TAG, "Renderer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping renderer", e)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        stopRenderer()
        scope.cancel()
    }
}