package com.malikhw.orbit.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Thin wrapper around SharedPreferences.
 * Mirrors the Settings struct from the Windows version.
 */
class OrbitPrefs(context: Context) {

    companion object {
        const val BG_BLACK          = 0
        const val BG_COLOR          = 1
        const val BG_IMAGE          = 2

        // Orientation constants — map to ActivityInfo.SCREEN_ORIENTATION_*
        const val ORIENT_PORTRAIT           = 0  // normal portrait
        const val ORIENT_LANDSCAPE          = 1  // landscape
        const val ORIENT_REVERSE_LANDSCAPE  = 2  // reversed landscape
        const val ORIENT_REVERSE_PORTRAIT   = 3  // reversed portrait

        private const val PREFS_NAME = "orbit_settings"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Getters / Setters ─────────────────────────────────────────────────────

    var speed: Int
        get()  = prefs.getInt("speed", 10)
        set(v) = prefs.edit { putInt("speed", v) }

    var fps: Int
        get()  = prefs.getInt("fps", 60)
        set(v) = prefs.edit { putInt("fps", v) }

    var bgMode: Int
        get()  = prefs.getInt("bg_mode", BG_BLACK)
        set(v) = prefs.edit { putInt("bg_mode", v) }

    var bgColorR: Float
        get()  = prefs.getFloat("bg_r", 0.12f)
        set(v) = prefs.edit { putFloat("bg_r", v) }

    var bgColorG: Float
        get()  = prefs.getFloat("bg_g", 0.12f)
        set(v) = prefs.edit { putFloat("bg_g", v) }

    var bgColorB: Float
        get()  = prefs.getFloat("bg_b", 0.12f)
        set(v) = prefs.edit { putFloat("bg_b", v) }

    /** URI string of the custom background image, or null. */
    var bgImageUri: String?
        get()  = prefs.getString("bg_image_uri", null)
        set(v) = prefs.edit { if (v != null) putString("bg_image_uri", v) else remove("bg_image_uri") }

    /** URI string of the custom cube image, or null (use bundled default). */
    var cubeImageUri: String?
        get()  = prefs.getString("cube_image_uri", null)
        set(v) = prefs.edit { if (v != null) putString("cube_image_uri", v) else remove("cube_image_uri") }

    var noGround: Boolean
        get()  = prefs.getBoolean("no_ground", false)
        set(v) = prefs.edit { putBoolean("no_ground", v) }

    var orbScale: Float
        get()  = prefs.getFloat("orb_scale", 1.0f)
        set(v) = prefs.edit { putFloat("orb_scale", v) }

    var orbCount: Int
        get()  = prefs.getInt("orb_count", 120)
        set(v) = prefs.edit { putInt("orb_count", v) }

    var cubeChance: Int
        get()  = prefs.getInt("cube_chance", 50)
        set(v) = prefs.edit { putInt("cube_chance", v) }

    var autoUpdateCheck: Boolean
        get()  = prefs.getBoolean("auto_update_check", true)
        set(v) = prefs.edit { putBoolean("auto_update_check", v) }

    /**
     * Screen orientation for the screensaver.
     * One of ORIENT_PORTRAIT, ORIENT_LANDSCAPE, ORIENT_REVERSE_LANDSCAPE, ORIENT_REVERSE_PORTRAIT.
     */
    var orientation: Int
        get()  = prefs.getInt("orientation", ORIENT_PORTRAIT)
        set(v) = prefs.edit { putInt("orientation", v) }
}
