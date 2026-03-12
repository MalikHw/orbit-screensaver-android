// In nativeSetBgBitmap and nativeSetCubeBitmap, replace the pixel copy loops.
// Android ARGB_8888 stores bytes as [R, G, B, A] in memory on little-endian ARM —
// no channel swap needed. The old code was swapping R↔B causing the color tint.

// ── REPLACE nativeSetBgBitmap body with this ──────────────────────────────────
JNIEXPORT void JNICALL
Java_com_malikhw_orbit_dream_OrbitRenderer_nativeSetBgBitmap(
        JNIEnv* env, jobject, jobject bitmap)
{
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    void* pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    int W = info.width, H = info.height;
    std::vector<uint8_t> buf(W * H * 4);
    auto* src = (uint8_t*)pixels;
    // ARGB_8888 on Android: each pixel is stored [R, G, B, A] in memory — copy as-is
    memcpy(buf.data(), src, W * H * 4);
    // Force alpha to 255 (wallpaper bitmaps can have garbage alpha)
    for (int i = 0; i < W * H; i++) buf[i*4+3] = 255;

    AndroidBitmap_unlockPixels(env, bitmap);

    {
        std::lock_guard<std::mutex> lk(g_bgMutex);
        g_bgPixels = std::move(buf);
        g_bgPixW = W; g_bgPixH = H;
    }
    g_bgDirty = true;
}

// ── REPLACE nativeSetCubeBitmap body with this ────────────────────────────────
JNIEXPORT void JNICALL
Java_com_malikhw_orbit_dream_OrbitRenderer_nativeSetCubeBitmap(
        JNIEnv* env, jobject, jobject bitmap)
{
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    void* pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    int W = info.width, H = info.height;
    std::vector<uint8_t> buf(W * H * 4);
    auto* src = (uint8_t*)pixels;
    // ARGB_8888 on Android: [R, G, B, A] in memory — copy as-is, preserve alpha
    memcpy(buf.data(), src, W * H * 4);

    AndroidBitmap_unlockPixels(env, bitmap);

    {
        std::lock_guard<std::mutex> lk(g_cubeMutex);
        g_cubePixels = std::move(buf);
        g_cubePixW = W; g_cubePixH = H;
    }
    g_cubeDirty = true;
}
