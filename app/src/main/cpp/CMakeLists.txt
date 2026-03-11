cmake_minimum_required(VERSION 3.22.1)
project(orbit_native)

# ── Box2D v2.4.1 (fetched at configure time via FetchContent) ──────────────────
include(FetchContent)
FetchContent_Declare(
    box2d
    GIT_REPOSITORY https://github.com/erincatto/box2d.git
    GIT_TAG        v2.4.1
    GIT_SHALLOW    TRUE
)
set(BOX2D_BUILD_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(BOX2D_BUILD_TESTBED    OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(box2d)

# ── stb_image (header-only, vendored in cpp/include/) ─────────────────────────
# Just needs to be in the include path; we define STB_IMAGE_IMPLEMENTATION
# in one .cpp file.

# ── Main JNI library ───────────────────────────────────────────────────────────
add_library(
    orbit_native
    SHARED
    orbit_jni.cpp
)

target_include_directories(orbit_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${box2d_SOURCE_DIR}/include
)

target_link_libraries(orbit_native
    box2d
    android
    log
    EGL
    GLESv2
    jnigraphics   # AndroidBitmap_*  (for wallpaper pixel access)
)

target_compile_options(orbit_native PRIVATE
    -O2
    -Wall
    -Wno-unused-parameter
)
