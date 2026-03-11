# Orbit Screensaver — Android

Android port of the Windows Orbit Screensaver.  
Physics-based orbs (Box2D) rendered with OpenGL ES 2.0, running as a native `DreamService`.

## Requirements

- Android 10+ (API 29)
- OpenGL ES 2.0 (all modern Android devices)

## Setup in Android Studio

1. Clone the repo
2. Open in Android Studio Hedgehog (2023.1) or newer
3. Let Gradle sync — it will fetch Box2D via CMake FetchContent automatically
4. Drop your orb images (`orb1.png` … `orb10.png`) and `cube.png` into `app/src/main/assets/`
5. Build & run

## Enabling the screensaver

1. Go to **Settings → Display → Screen saver** (or **Daydream** on some devices)
2. Select **Orbit**
3. Tap the settings gear to configure
4. Enable "Start now" or set it to activate on charge

> On Samsung One UI: Settings → Display → Screen saver → Orbit

## Architecture

```
Kotlin (DreamService)
  └── SurfaceView
        └── SurfaceHolder.Callback
              └── JNI → orbit_jni.cpp
                    ├── ANativeWindow + EGL context
                    ├── GLES2 shaders (textured quad + color quad)
                    ├── Box2D world (circles + box for cube)
                    └── Render loop thread
```

## Assets

Place these in `app/src/main/assets/`:
- `orb1.png` … `orb10.png` — orb textures (any size, RGBA PNG)
- `cube.png` — default cube texture (square, RGBA PNG)

Users can override the cube image from the settings screen (gallery picker).

## Building a release APK

Push to `main` — GitHub Actions will build, sign with the debug key,  
and create a release tagged `YYYYMMDD-HHMMSS` with `orbit-screensaver.apk`.

For a proper release, add your keystore as GitHub secrets and update the workflow.

## Differences from Windows version

| Windows | Android |
|---|---|
| `.scr` + WinMain | `DreamService` |
| ImGui settings | Jetpack Compose + Material 3 |
| `settings.ini` | `SharedPreferences` |
| SDL2 + OpenGL 2.x | ANativeWindow + EGL + GLES2 |
| Desktop screenshot | `WallpaperManager` |
| Mesa3D fallback | Removed (not needed) |
| urlmon / winhttp | `HttpURLConnection` |
| System installer via ShellExecute | `DownloadManager` + `FileProvider` |

## by MalikHw47
- https://malikhw.github.io  
- https://github.com/MalikHw  
- https://ko-fi.com/malikhw47
