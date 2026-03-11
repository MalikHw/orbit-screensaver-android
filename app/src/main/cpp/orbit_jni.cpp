// orbit_jni.cpp — Android port of orbit screensaver
// SurfaceView → ANativeWindow → EGL → GLES2 + Box2D

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

#include <box2d/box2d.h>

#include <cmath>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <mutex>
#include <chrono>

#define LOG_TAG "OrbitNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr float PPM        = 40.0f;
static constexpr int   NUM_ORBS   = 10;
static constexpr int   PLAYER_SIZE = 80;

// ─── Settings (written from Kotlin via nativeSetSettings) ────────────────────
struct Settings {
    int   speed         = 10;
    int   fps           = 60;
    int   bg_mode       = 0;   // 0=black 1=color 2=image 3=wallpaper 4=blur_wallpaper
    float bg_color[3]   = {0.12f, 0.12f, 0.12f};
    bool  no_ground     = false;
    float orb_scale     = 1.0f;
    int   orb_count     = 120;
    int   cube_chance   = 50;
    // paths resolved on Kotlin side, pixels pushed via nativeSetBgPixels / nativeSetCubePixels
};

// ─── Shader sources ──────────────────────────────────────────────────────────
static const char* kVtxSrc = R"(
attribute vec2 aPos;
attribute vec2 aUV;
uniform   mat4 uMVP;
varying   vec2 vUV;
void main() {
    vUV = aUV;
    gl_Position = uMVP * vec4(aPos, 0.0, 1.0);
}
)";

static const char* kFragTex = R"(
precision mediump float;
varying vec2 vUV;
uniform sampler2D uTex;
void main() {
    gl_FragColor = texture2D(uTex, vUV);
}
)";

static const char* kFragCol = R"(
precision mediump float;
uniform vec4 uColor;
void main() {
    gl_FragColor = uColor;
}
)";

// ─── GL helpers ──────────────────────────────────────────────────────────────
static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char buf[512]; glGetShaderInfoLog(s, 512, nullptr, buf); LOGE("Shader: %s", buf); }
    return s;
}
static GLuint linkProgram(const char* vtx, const char* frg) {
    GLuint p = glCreateProgram();
    glAttachShader(p, compileShader(GL_VERTEX_SHADER,   vtx));
    glAttachShader(p, compileShader(GL_FRAGMENT_SHADER, frg));
    glLinkProgram(p);
    return p;
}

struct GlTex { GLuint id = 0; int w = 0, h = 0; bool ok = false; };

static GlTex uploadTexture(const unsigned char* rgba, int w, int h) {
    GlTex t; t.w = w; t.h = h;
    glGenTextures(1, &t.id);
    glBindTexture(GL_TEXTURE_2D, t.id);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    t.ok = true;
    return t;
}

static GlTex loadAssetTexture(AAssetManager* mgr, const char* name) {
    GlTex t;
    AAsset* a = AAssetManager_open(mgr, name, AASSET_MODE_BUFFER);
    if (!a) { LOGE("Asset not found: %s", name); return t; }
    int w, h, ch;
    auto* data = stbi_load_from_memory(
        (const stbi_uc*)AAsset_getBuffer(a), (int)AAsset_getLength(a),
        &w, &h, &ch, 4);
    AAsset_close(a);
    if (!data) { LOGE("stbi failed: %s", name); return t; }
    t = uploadTexture(data, w, h);
    stbi_image_free(data);
    return t;
}

// ─── Ortho matrix (column-major) ─────────────────────────────────────────────
static void ortho2D(float* m, float l, float r, float b, float top) {
    memset(m, 0, 64);
    m[0]  =  2.f/(r-l);
    m[5]  =  2.f/(top-b);
    m[10] = -1.f;
    m[12] = -(r+l)/(r-l);
    m[13] = -(top+b)/(top-b);
    m[15] =  1.f;
}

// ─── Quad drawing ─────────────────────────────────────────────────────────────
static void drawQuad(GLuint prog, const float* mvp,
                     float cx, float cy, float w, float h, float angleDeg,
                     GLuint texId = 0, float r=1,float g=1,float b=1,float a=1)
{
    glUseProgram(prog);

    float hw = w * 0.5f, hh = h * 0.5f;
    float rad = angleDeg * (float)M_PI / 180.f;
    float ca = cosf(rad), sa = sinf(rad);

    // rotate corners around center
    float verts[4][4]; // x,y,u,v
    float corners[4][2] = {{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
    float uvs[4][2]     = {{0,0},{1,0},{1,1},{0,1}};
    for (int i = 0; i < 4; i++) {
        float x = corners[i][0], y = corners[i][1];
        verts[i][0] = cx + x*ca - y*sa;
        verts[i][1] = cy + x*sa + y*ca;
        verts[i][2] = uvs[i][0];
        verts[i][3] = uvs[i][1];
    }

    GLint aPos = glGetAttribLocation(prog, "aPos");
    GLint aUV  = glGetAttribLocation(prog, "aUV");
    glUniformMatrix4fv(glGetUniformLocation(prog, "uMVP"), 1, GL_FALSE, mvp);

    if (texId) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texId);
        glUniform1i(glGetUniformLocation(prog, "uTex"), 0);
    } else {
        glUniform4f(glGetUniformLocation(prog, "uColor"), r, g, b, a);
    }

    glEnableVertexAttribArray(aPos);
    glEnableVertexAttribArray(aUV);
    glVertexAttribPointer(aPos, 2, GL_FLOAT, GL_FALSE, 4*sizeof(float), verts);
    glVertexAttribPointer(aUV,  2, GL_FLOAT, GL_FALSE, 4*sizeof(float), &verts[0][2]);

    GLushort idx[] = {0,1,2, 0,2,3};
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, idx);

    glDisableVertexAttribArray(aPos);
    glDisableVertexAttribArray(aUV);
}

static void drawFullscreenTex(GLuint prog, const float* mvp,
                               int W, int H, GLuint texId,
                               float scaleX=1.f, float scaleY=1.f,
                               float offX=0.f, float offY=0.f) {
    // draw with custom UV for zoom/tile — simplified to stretch here
    (void)scaleX; (void)scaleY; (void)offX; (void)offY;
    drawQuad(prog, mvp, W*0.5f, H*0.5f, (float)W, (float)H, 0.f, texId);
}

// ─── Box blur (same algorithm as Windows version) ────────────────────────────
static void boxBlur(unsigned char* px, int W, int H, int radius) {
    auto* tmp = new unsigned char[W * H * 4];
    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
        int rr=0,gg=0,bb=0,cnt=0;
        for (int k=-radius;k<=radius;k++){
            int nx=x+k; if(nx<0||nx>=W)continue;
            int idx=(y*W+nx)*4; rr+=px[idx];gg+=px[idx+1];bb+=px[idx+2];cnt++;
        }
        int idx=(y*W+x)*4; tmp[idx]=rr/cnt;tmp[idx+1]=gg/cnt;tmp[idx+2]=bb/cnt;tmp[idx+3]=255;
    }
    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
        int rr=0,gg=0,bb=0,cnt=0;
        for (int k=-radius;k<=radius;k++){
            int ny=y+k; if(ny<0||ny>=H)continue;
            int idx=(ny*W+x)*4; rr+=tmp[idx];gg+=tmp[idx+1];bb+=tmp[idx+2];cnt++;
        }
        int idx=(y*W+x)*4; px[idx]=rr/cnt;px[idx+1]=gg/cnt;px[idx+2]=bb/cnt;px[idx+3]=255;
    }
    delete[] tmp;
}

// ─── Global state ─────────────────────────────────────────────────────────────
static std::atomic<bool>  g_running{false};
static std::atomic<bool>  g_surfaceReady{false};
static ANativeWindow*     g_window = nullptr;
static std::mutex         g_windowMutex;
static Settings           g_settings;
static std::mutex         g_settingsMutex;
static AAssetManager*     g_assetMgr = nullptr;

// bg pixel buffer set from Kotlin (wallpaper bitmap)
static std::vector<uint8_t> g_bgPixels;
static int                  g_bgPixW = 0, g_bgPixH = 0;
static std::atomic<bool>    g_bgDirty{false};
static std::mutex           g_bgMutex;

// custom cube pixel buffer set from Kotlin
static std::vector<uint8_t> g_cubePixels;
static int                  g_cubePixW = 0, g_cubePixH = 0;
static std::atomic<bool>    g_cubeDirty{false};
static std::mutex           g_cubeMutex;

static std::thread g_renderThread;

// ─── Ball struct ──────────────────────────────────────────────────────────────
struct Ball {
    b2Body* body;
    float   radius;
    int     orbIdx;
    bool    isPlayer;
};

// ─── Render thread ────────────────────────────────────────────────────────────
static void renderLoop() {
    // ── EGL setup ────────────────────────────────────────────────────────────
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(display, nullptr, nullptr);

    const EGLint cfgAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config; EGLint numCfg;
    eglChooseConfig(display, cfgAttribs, &config, 1, &numCfg);

    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs);

    ANativeWindow* win;
    { std::lock_guard<std::mutex> lk(g_windowMutex); win = g_window; }

    EGLSurface surface = eglCreateWindowSurface(display, config, win, nullptr);
    eglMakeCurrent(display, surface, surface, context);

    EGLint W = 0, H = 0;
    eglQuerySurface(display, surface, EGL_WIDTH,  &W);
    eglQuerySurface(display, surface, EGL_HEIGHT, &H);

    LOGI("Render loop started %dx%d", W, H);

    // ── Compile shaders ───────────────────────────────────────────────────────
    GLuint progTex = linkProgram(kVtxSrc, kFragTex);
    GLuint progCol = linkProgram(kVtxSrc, kFragCol);

    float mvp[16]; ortho2D(mvp, 0, (float)W, (float)H, 0);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glViewport(0, 0, W, H);

    // ── Load orb textures from assets ─────────────────────────────────────────
    GlTex orbTex[NUM_ORBS];
    for (int i = 0; i < NUM_ORBS; i++) {
        char name[32]; snprintf(name, sizeof(name), "orb%d.png", i + 1);
        orbTex[i] = loadAssetTexture(g_assetMgr, name);
    }

    // ── Load default cube texture ─────────────────────────────────────────────
    GlTex cubeTex = loadAssetTexture(g_assetMgr, "cube.png");

    // ── BG / cube textures (may be overridden from Kotlin) ────────────────────
    GlTex bgTex;

    auto uploadBgIfDirty = [&]() {
        if (!g_bgDirty.load()) return;
        g_bgDirty = false;
        std::lock_guard<std::mutex> lk(g_bgMutex);
        if (bgTex.ok) glDeleteTextures(1, &bgTex.id);
        bgTex = uploadTexture(g_bgPixels.data(), g_bgPixW, g_bgPixH);
    };

    auto uploadCubeIfDirty = [&]() {
        if (!g_cubeDirty.load()) return;
        g_cubeDirty = false;
        std::lock_guard<std::mutex> lk(g_cubeMutex);
        if (cubeTex.ok) glDeleteTextures(1, &cubeTex.id);
        cubeTex = uploadTexture(g_cubePixels.data(), g_cubePixW, g_cubePixH);
    };

    srand((unsigned)time(nullptr));

    // ── Outer loop: restart simulation when one cycle ends ────────────────────
    while (g_running.load()) {

        Settings s;
        { std::lock_guard<std::mutex> lk(g_settingsMutex); s = g_settings; }

        int fps = std::max(1, std::min(500, s.fps));
        float speedMult  = s.speed / 10.0f;
        int   numBalls   = std::max(1, s.orb_count);
        int   dropTime   = std::max(1, (int)(20.f / speedMult));

        b2Vec2 gravity(0.f, 9.8f * speedMult * 3.f);
        b2World world(gravity);

        auto makeWall = [&](float x1,float y1,float x2,float y2) {
            b2BodyDef bd; bd.type = b2_staticBody;
            b2Body* body = world.CreateBody(&bd);
            b2EdgeShape es; es.SetTwoSided(b2Vec2(x1/PPM,y1/PPM), b2Vec2(x2/PPM,y2/PPM));
            b2FixtureDef fd; fd.shape = &es; fd.restitution = 0.5f; fd.friction = 0.7f;
            body->CreateFixture(&fd);
            return body;
        };
        makeWall(0,0,0,(float)H);
        makeWall((float)W,0,(float)W,(float)H);
        b2Body* wallBottom = nullptr;
        if (!s.no_ground) wallBottom = makeWall(0,(float)H,(float)W,(float)H);

        std::vector<Ball> balls;
        int  globalTime   = 0;
        bool fillingDone  = false;
        bool draining     = false;
        int  nextSpawn    = 0;
        bool playerSpawned = false;
        int64_t allSpawnedAt = 0;

        const float physStep = 1.f / fps;
        float physAccum = 0.f;

        auto nowMs = []() {
            return std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
        };

        int64_t lastTick = nowMs();
        bool simRunning  = true;

        while (simRunning && g_running.load()) {
            globalTime++;

            // check if surface is still valid
            { std::lock_guard<std::mutex> lk(g_windowMutex);
              if (!g_window) { simRunning = false; break; } }

            uploadBgIfDirty();
            uploadCubeIfDirty();

            // ── Spawn balls ───────────────────────────────────────────────────
            while (nextSpawn < numBalls && globalTime >= dropTime * nextSpawn) {
                float radius = (40 + rand() % 20) * s.orb_scale;
                b2BodyDef bd; bd.type = b2_dynamicBody;
                bd.position.Set(
                    ((float)W * 0.8f / numBalls * (1 + rand() % (numBalls * 2))) / PPM,
                    -250.f / PPM);
                b2Body* body = world.CreateBody(&bd);
                b2CircleShape cs; cs.m_radius = radius / PPM;
                b2FixtureDef fd; fd.shape = &cs; fd.density = 1.f;
                fd.restitution = 0.5f; fd.friction = 1.f;
                body->CreateFixture(&fd);
                body->ApplyLinearImpulse(
                    b2Vec2((10 - rand() % 21) * 0.05f, 0),
                    body->GetWorldCenter(), true);
                Ball ball; ball.body = body; ball.radius = radius;
                ball.orbIdx = rand() % NUM_ORBS; ball.isPlayer = false;
                balls.push_back(ball);
                nextSpawn++;
            }

            // ── Spawn cube (player) ───────────────────────────────────────────
            if (!playerSpawned && nextSpawn >= numBalls / 2) {
                playerSpawned = true;
                if ((rand() % 100) < s.cube_chance) {
                    b2BodyDef bd; bd.type = b2_dynamicBody;
                    bd.position.Set((float)W * 0.5f / PPM, -400.f / PPM);
                    b2Body* body = world.CreateBody(&bd);
                    float hsize = PLAYER_SIZE * 0.5f * s.orb_scale / PPM;
                    b2PolygonShape ps; ps.SetAsBox(hsize, hsize);
                    b2FixtureDef fd; fd.shape = &ps; fd.density = 1.f;
                    fd.restitution = 0.5f; fd.friction = 0.7f;
                    body->CreateFixture(&fd);
                    Ball ball; ball.body = body;
                    ball.radius = PLAYER_SIZE * 0.5f * s.orb_scale;
                    ball.orbIdx = 0; ball.isPlayer = true;
                    balls.push_back(ball);
                }
            }

            // ── Drain floor ───────────────────────────────────────────────────
            if (!s.no_ground && !fillingDone && nextSpawn >= numBalls) {
                if (allSpawnedAt == 0) allSpawnedAt = nowMs();
                int64_t delay = 5000 + rand() % 1001;
                if (nowMs() - allSpawnedAt >= delay) {
                    fillingDone = true; draining = true;
                    if (wallBottom) { world.DestroyBody(wallBottom); wallBottom = nullptr; }
                }
            }
            if (!s.no_ground && draining) {
                bool allOff = true;
                for (auto& b : balls)
                    if (b.body->GetPosition().y * PPM < H + 300) { allOff = false; break; }
                if (allOff) simRunning = false;
            }
            if (s.no_ground && globalTime > numBalls * dropTime + 500) simRunning = false;

            // ── Physics step ─────────────────────────────────────────────────
            int64_t now = nowMs();
            physAccum += (now - lastTick) / 1000.f;
            lastTick = now;
            while (physAccum >= physStep) {
                world.Step(physStep, 8, 3);
                physAccum -= physStep;
            }

            // ── Render ────────────────────────────────────────────────────────
            int bgMode;
            { std::lock_guard<std::mutex> lk(g_settingsMutex); bgMode = g_settings.bg_mode; }

            if ((bgMode == 3 || bgMode == 4) && bgTex.ok) {
                glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
                drawFullscreenTex(progTex, mvp, W, H, bgTex.id);
            } else if (bgMode == 2 && bgTex.ok) {
                glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
                drawFullscreenTex(progTex, mvp, W, H, bgTex.id);
            } else if (bgMode == 1) {
                float* c; { std::lock_guard<std::mutex> lk(g_settingsMutex); c = g_settings.bg_color; }
                glClearColor(c[0], c[1], c[2], 1); glClear(GL_COLOR_BUFFER_BIT);
            } else {
                glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
            }

            for (auto& b : balls) {
                float px = b.body->GetPosition().x * PPM;
                float py = b.body->GetPosition().y * PPM;
                float ang = b.body->GetAngle() * 180.f / (float)M_PI;

                if (b.isPlayer) {
                    float sz = PLAYER_SIZE * s.orb_scale;
                    if (cubeTex.ok)
                        drawQuad(progTex, mvp, px, py, sz, sz, ang, cubeTex.id);
                    else
                        drawQuad(progCol, mvp, px, py, sz, sz, ang, 0, 0.78f,0.39f,0.39f,1.f);
                } else {
                    float d = b.radius * 2.f;
                    GlTex& ot = orbTex[b.orbIdx % NUM_ORBS];
                    if (ot.ok)
                        drawQuad(progTex, mvp, px, py, d, d, ang, ot.id);
                    else
                        drawQuad(progCol, mvp, px, py, d, d, ang, 0, 0.39f,0.39f,0.78f,1.f);
                }
            }

            eglSwapBuffers(display, surface);

            // ── Frame cap ────────────────────────────────────────────────────
            int64_t elapsed = nowMs() - now;
            int64_t target  = 1000 / fps;
            if (elapsed < target) {
                struct timespec ts;
                ts.tv_sec  = 0;
                ts.tv_nsec = (target - elapsed) * 1000000L;
                nanosleep(&ts, nullptr);
            }
        } // inner sim loop

        balls.clear();
    } // outer restart loop

    // ── Cleanup ───────────────────────────────────────────────────────────────
    for (int i = 0; i < NUM_ORBS; i++)
        if (orbTex[i].ok) glDeleteTextures(1, &orbTex[i].id);
    if (cubeTex.ok) glDeleteTextures(1, &cubeTex.id);
    if (bgTex.ok)   glDeleteTextures(1, &bgTex.id);
    glDeleteProgram(progTex);
    glDeleteProgram(progCol);

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    LOGI("Render loop ended");
}

// ─── JNI exports ──────────────────────────────────────────────────────────────
extern "C" {

JNIEXPORT void JNICALL
Java_com_malikhw_orbit_dream_OrbitRenderer_nativeSetAssetManager(
        JNIEnv* env, jobject, jobject assetMgr) {
    g_assetMgr = AAssetManager_fromJava(env, assetMgr);
}

JNIEXPORT void JNICALL
Java_com_malikhw_orbit_dream_OrbitRenderer_nativeSetSettings(
        JNIEnv* env, jobject,
        jint speed, jint fps, jint bgMode,
        jfloat bgR, jfloat bgG, jfloat bgB,
        jboolean noGround, jfloat orbScale,
        jint orbCount, jint cubeChance)
{
    std::lock_guard<std::mutex> lk(g_settingsMutex);
    g_settings.speed       = speed;
    g_settings.fps         = fps;
    g_settings.bg_mode     = bgMode;
    g_settings.bg_color[0] = bgR;
    g_settings.bg_color[1] = bgG;
    g_settings.bg_color[2] = bgB;
    g_settings.no_ground   = noGround;
    g_settings.orb_scale   = orbScale;
    g_settings.orb_count   = orbCount;
    g_settings.cube_chance = cubeChance;
}

// Called from Kotlin with ARGB_8888 bitmap (wallpaper, already blurred if needed)
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
    // Android bitmap is ARGB_8888 = stored as BGRA on little-endian; convert to RGBA
    auto* src = (uint8_t*)pixels;
    for (int i = 0; i < W * H; i++) {
        buf[i*4+0] = src[i*4+2]; // R ← B
        buf[i*4+1] = src[i*4+1]; // G
        buf[i*4+2] = src[i*4+0]; // B ← R
        buf[i*4+3] = 255;
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    {
        std::lock_guard<std::mutex> lk(g_bgMutex);
        g_bgPixels = std::move(buf);
        g_bgPixW = W; g_bgPixH = H;
    }
    g_bgDirty = true;
}

// Called from Kotlin with decoded cube bitmap (gallery or default)
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
    for (int i = 0; i < W * H; i++) {
        buf[i*4+0] = src[i*4+2];
        buf[i*4+1] = src[i*4+1];
        buf[i*4+2] = src[i*4+0];
        buf[i*4+3] = src[i*4+3];
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    {
        std::lock_guard<std::mutex> lk(g_cubeMutex);
        g_cubePixels = std::move(buf);
        g_cubePixW = W; g_cubePixH = H;
    }
    g_cubeDirty = true;
}

JNIEXPORT void JNICALL
Java_com_malikhw_orbit_dream_OrbitRenderer_nativeStart(
        JNIEnv* env, jobject, jobject surface)
{
    if (g_running.load()) return;
    {
        std::lock_guard<std::mutex> lk(g_windowMutex);
        g_window = ANativeWindow_fromSurface(env, surface);
    }
    g_running = true;
    g_renderThread = std::thread(renderLoop);
}

JNIEXPORT void JNICALL
Java_com_malikhw_orbit_dream_OrbitRenderer_nativeStop(JNIEnv*, jobject)
{
    g_running = false;
    if (g_renderThread.joinable()) g_renderThread.join();
    {
        std::lock_guard<std::mutex> lk(g_windowMutex);
        if (g_window) { ANativeWindow_release(g_window); g_window = nullptr; }
    }
}

JNIEXPORT void JNICALL
Java_com_malikhw_orbit_dream_OrbitRenderer_nativeSurfaceDestroyed(JNIEnv*, jobject)
{
    g_running = false;
    if (g_renderThread.joinable()) g_renderThread.join();
    {
        std::lock_guard<std::mutex> lk(g_windowMutex);
        if (g_window) { ANativeWindow_release(g_window); g_window = nullptr; }
    }
}

} // extern "C"
