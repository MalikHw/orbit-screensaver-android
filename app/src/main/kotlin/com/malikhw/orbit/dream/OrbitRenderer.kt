package com.malikhw.orbit.dream

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface

object OrbitRenderer {

    init {
        System.loadLibrary("orbit_native")
    }

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
        cubeChance: Int,
        gravityDir: Int   // 0=down 1=left 2=up 3=right
    )

    external fun nativeSetBgBitmap(bitmap: Bitmap)
    external fun nativeSetCubeBitmap(bitmap: Bitmap)

    external fun nativeStart(surface: Surface)
    external fun nativeStop()
    external fun nativeSurfaceDestroyed()
}
