package com.malikhw.orbit.dream

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface

/**
 * Thin Kotlin wrapper around the native JNI functions.
 * All calls are direct – no queueing needed since the render thread
 * picks up state changes on the next frame via atomics / mutexes.
 */
object OrbitRenderer {

    init {
        System.loadLibrary("orbit_native")
    }

    // ── JNI declarations ─────────────────────────────────────────────────────

    external fun nativeSetAssetManager(assetManager: AssetManager)

    external fun nativeSetSettings(
        speed: Int,
        fps: Int,
        bgMode: Int,
        bgR: Float,
        bgG: Float,
        bgB: Float,
        noGround: Boolean,
        orbScale: Float,
        orbCount: Int,
        cubeChance: Int
    )

    /** Pass an ARGB_8888 bitmap (wallpaper, pre-blurred on Kotlin side). */
    external fun nativeSetBgBitmap(bitmap: Bitmap)

    /** Pass an ARGB_8888 cube bitmap (gallery pick or default). */
    external fun nativeSetCubeBitmap(bitmap: Bitmap)

    external fun nativeStart(surface: Surface)
    external fun nativeStop()
    external fun nativeSurfaceDestroyed()
}
