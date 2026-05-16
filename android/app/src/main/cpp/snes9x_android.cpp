#include <jni.h>
#include <android/log.h>
#include <sys/stat.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>

#include "snes9x.h"
#include "memmap.h"
#include "apu/apu.h"
#include "gfx.h"
#include "snapshot.h"
#include "controls.h"
#include "cheats.h"
#include "movie.h"
#include "display.h"
#include "fscompat.h"

#define LOG_TAG "snes9x"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

// ── State ─────────────────────────────────────────────────────────────────────

static std::string g_saveDir;
static std::string g_romDir;

// Frame buffer – RGB565, big enough for any SNES output mode
static uint16_t g_frameBuffer[MAX_SNES_WIDTH * MAX_SNES_HEIGHT];
static int      g_frameWidth  = SNES_WIDTH;
static int      g_frameHeight = SNES_HEIGHT;
static std::mutex g_frameMutex;

// Audio ring buffer (stereo int16 interleaved)
static std::vector<int16_t> g_audioBuffer;
static std::mutex g_audioMutex;

// Button state reported to the emulator each frame (bit i = button index i)
static std::atomic<uint32_t> g_buttons{0};

// Guard against double-init
static bool g_initialized = false;

// ── Directory helpers ─────────────────────────────────────────────────────────

static void ensure_dir(const std::string &path)
{
    mkdir(path.c_str(), 0755);
}

static void create_save_dirs(const std::string &base)
{
    ensure_dir(base + "/sram");
    ensure_dir(base + "/states");
    ensure_dir(base + "/cheats");
    ensure_dir(base + "/bios");
    ensure_dir(base + "/patches");
    ensure_dir(base + "/screenshots");
}

// ── snes9x port: directory / filename interface ───────────────────────────────

std::string S9xGetDirectory(s9x_getdirtype type)
{
    switch (type)
    {
        case SRAM_DIR:        return g_saveDir + "/sram";
        case SNAPSHOT_DIR:    return g_saveDir + "/states";
        case SCREENSHOT_DIR:  return g_saveDir + "/screenshots";
        case PATCH_DIR:       return g_saveDir + "/patches";
        case CHEAT_DIR:       return g_saveDir + "/cheats";
        case BIOS_DIR:        return g_saveDir + "/bios";
        case ROMFILENAME_DIR: return g_romDir;
        case ROM_DIR:         return g_romDir;
        default:              return g_saveDir;
    }
}

std::string S9xGetFilenameInc(std::string, s9x_getdirtype) { return ""; }

// ── snes9x port: graphics update pipeline ────────────────────────────────────

bool8 S9xInitUpdate() { return TRUE; }

bool8 S9xDeinitUpdate(int width, int height)
{
    std::lock_guard<std::mutex> lock(g_frameMutex);
    g_frameWidth  = width;
    g_frameHeight = height;
    // GFX.Pitch is in bytes; copy row-by-row because pitch ≥ width*2
    for (int y = 0; y < height; y++)
        memcpy(g_frameBuffer + y * width,
               reinterpret_cast<const uint8_t *>(GFX.Screen) + y * GFX.Pitch,
               width * sizeof(uint16_t));
    return TRUE;
}

bool8 S9xContinueUpdate(int width, int height)
{
    return S9xDeinitUpdate(width, height);
}


// ── snes9x port: audio sync ───────────────────────────────────────────────────

void S9xSyncSpeed()
{
    if (Settings.Mute)
    {
        S9xClearSamples();
        return;
    }
    std::lock_guard<std::mutex> lock(g_audioMutex);
    size_t avail = S9xGetSampleCount();
    size_t base  = g_audioBuffer.size();
    g_audioBuffer.resize(base + avail);
    S9xMixSamples(reinterpret_cast<uint8 *>(g_audioBuffer.data() + base), avail);
}

// ── snes9x port: messaging / system ──────────────────────────────────────────

void S9xMessage(int, int, const char *s) { LOGI("%s", s); }
void S9xExit()                           { exit(0); }
bool8 S9xOpenSoundDevice()               { return TRUE; }

// ── snes9x port: display interface (display.h) ───────────────────────────────

void S9xPutImage(int, int)                    {}
void S9xInitDisplay(int, char **)             {}
void S9xDeinitDisplay()                       {}
void S9xTextMode()                            {}
void S9xGraphicsMode()                        {}
void S9xToggleSoundChannel(int)               {}
const char *S9xStringInput(const char *)      { return ""; }
void S9xExtraUsage()                          {}
void S9xParseArg(char **, int &, int)         {}
void S9xSetTitle(const char *)                {}
void S9xInitInputDevices()                    {}
void S9xProcessEvents(bool8)                  {}
const char *S9xSelectFilename(const char *, const char *, const char *, const char *)
{
    return nullptr;
}

bool8 S9xOpenSnapshotFile(const char *path, bool8 write, STREAM *stream)
{
    *stream = OPEN_STREAM(path, write ? "wb" : "rb");
    return *stream != nullptr;
}

void S9xCloseSnapshotFile(STREAM stream) { CLOSE_STREAM(stream); }

// ── snes9x port: input polling (controls.h) ──────────────────────────────────

bool S9xPollButton(uint32, bool *)            { return false; }
bool S9xPollPointer(uint32, int16 *, int16 *) { return false; }
bool S9xPollAxis(uint32, int16 *)             { return false; }
void S9xHandlePortCommand(s9xcommand_t, int16, int16) {}

// ── Button mapping initialisation ────────────────────────────────────────────
// Button indices match RETRO_DEVICE_ID_JOYPAD_* (also used by Snes9xLib.kt):
//   0=B  1=Y  2=Select  3=Start  4=Up  5=Down  6=Left  7=Right
//   8=A  9=X  10=L  11=R

static void map_buttons()
{
#define MK(pad, btn) (((pad) << 4) | (btn))
#define MB(idx, cmd) S9xMapButton(MK(1, idx), S9xGetCommandT(cmd), false)

    MB( 8, "Joypad1 A");
    MB( 0, "Joypad1 B");
    MB( 9, "Joypad1 X");
    MB( 1, "Joypad1 Y");
    MB( 2, "{Joypad1 Select,Mouse1 L}");
    MB( 3, "{Joypad1 Start,Mouse1 R}");
    MB(10, "Joypad1 L");
    MB(11, "Joypad1 R");
    MB( 6, "Joypad1 Left");
    MB( 7, "Joypad1 Right");
    MB( 4, "Joypad1 Up");
    MB( 5, "Joypad1 Down");

#undef MB
#undef MK
}

// ── JNI interface ─────────────────────────────────────────────────────────────

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_snes9x_android_Snes9xLib_nativeInit(JNIEnv *env, jobject, jstring jSaveDir)
{
    const char *raw = env->GetStringUTFChars(jSaveDir, nullptr);
    g_saveDir = raw;
    env->ReleaseStringUTFChars(jSaveDir, raw);
    create_save_dirs(g_saveDir);

    if (g_initialized) return JNI_TRUE;

    memset(&Settings, 0, sizeof(Settings));
    Settings.MouseMaster              = TRUE;
    Settings.SuperScopeMaster         = TRUE;
    Settings.JustifierMaster          = TRUE;
    Settings.MultiPlayer5Master       = TRUE;
    Settings.MacsRifleMaster          = TRUE;
    Settings.FrameTimePAL             = 20000;
    Settings.FrameTimeNTSC            = 16667;
    Settings.SixteenBitSound          = TRUE;
    Settings.Stereo                   = TRUE;
    Settings.SoundPlaybackRate        = 32040;
    Settings.SoundInputRate           = 32040;
    Settings.Transparency             = TRUE;
    Settings.AutoDisplayMessages      = TRUE;
    Settings.InitialInfoStringTimeout = 120;
    Settings.HDMATimingHack           = 100;
    Settings.BlockInvalidVRAMAccessMaster = TRUE;
    Settings.SeparateEchoBuffer       = FALSE;
    Settings.AutoSaveDelay            = 1;
    Settings.DontSaveOopsSnapshot     = TRUE;
    CPU.Flags = 0;

    if (!Memory.Init() || !S9xInitAPU())
    {
        Memory.Deinit();
        S9xDeinitAPU();
        LOGE("Memory or APU init failed");
        return JNI_FALSE;
    }

    S9xInitSound(32);
    S9xSetSoundMute(FALSE);
    S9xSetSamplesAvailableCallback(nullptr, nullptr);
    S9xGraphicsInit();
    S9xInitInputDevices();
    S9xSetController(0, CTL_JOYPAD, 0, 0, 0, 0);
    S9xSetController(1, CTL_JOYPAD, 1, 0, 0, 0);
    S9xUnmapAllControls();
    map_buttons();

    g_initialized = true;
    LOGI("Emulator initialised, saveDir=%s", g_saveDir.c_str());
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_snes9x_android_Snes9xLib_nativeLoadRom(JNIEnv *env, jobject, jstring jPath)
{
    const char *path = env->GetStringUTFChars(jPath, nullptr);

    // Derive ROM directory for MSU-1 / patch lookups
    std::string romPath(path);
    auto slash = romPath.rfind('/');
    g_romDir = (slash != std::string::npos) ? romPath.substr(0, slash) : ".";

    bool8 loaded = Memory.LoadROM(path);
    env->ReleaseStringUTFChars(jPath, path);

    if (loaded)
    {
        Memory.ClearSRAM();
        std::string sramPath = S9xGetFilename(".srm", SRAM_DIR);
        Memory.LoadSRAM(sramPath.c_str());
        LOGI("ROM loaded OK, SRAM path=%s", sramPath.c_str());
    }
    else
    {
        LOGE("ROM load failed");
    }
    return loaded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_snes9x_android_Snes9xLib_nativeRunFrame(JNIEnv *, jobject)
{
    // Push current button state into the snes9x controls system
    uint32_t btns = g_buttons.load(std::memory_order_relaxed);
    for (int i = 0; i < 12; i++)
        S9xReportButton(((1 << 4) | i), (btns >> i) & 1u);

    IPPU.RenderThisFrame = TRUE;
    S9xSetSoundMute(FALSE);
    S9xMainLoop();
}

JNIEXPORT jobject JNICALL
Java_com_snes9x_android_Snes9xLib_nativeGetFrameBuffer(JNIEnv *env, jobject)
{
    // Caller must not hold the frame mutex – we return a view of the buffer.
    // Since this is called from the GL thread right after nativeRunFrame
    // completes, no extra lock is needed here; S9xDeinitUpdate already ran.
    return env->NewDirectByteBuffer(g_frameBuffer, sizeof(g_frameBuffer));
}

JNIEXPORT jint JNICALL
Java_com_snes9x_android_Snes9xLib_nativeGetFrameWidth(JNIEnv *, jobject)
{
    return g_frameWidth;
}

JNIEXPORT jint JNICALL
Java_com_snes9x_android_Snes9xLib_nativeGetFrameHeight(JNIEnv *, jobject)
{
    return g_frameHeight;
}

JNIEXPORT jint JNICALL
Java_com_snes9x_android_Snes9xLib_nativeGetAudioSamples(JNIEnv *env, jobject,
                                                          jshortArray jBuf,
                                                          jint        maxSamples)
{
    std::lock_guard<std::mutex> lock(g_audioMutex);
    if (g_audioBuffer.empty()) return 0;

    jsize count = static_cast<jsize>(
        std::min(g_audioBuffer.size(), static_cast<size_t>(maxSamples)));
    env->SetShortArrayRegion(jBuf, 0, count, g_audioBuffer.data());

    // Remove the samples we just copied
    g_audioBuffer.erase(g_audioBuffer.begin(), g_audioBuffer.begin() + count);
    return count;
}

JNIEXPORT void JNICALL
Java_com_snes9x_android_Snes9xLib_nativeSetButton(JNIEnv *, jobject,
                                                    jint button, jboolean pressed)
{
    uint32_t cur = g_buttons.load(std::memory_order_relaxed);
    if (pressed)
        cur |=  (1u << button);
    else
        cur &= ~(1u << button);
    g_buttons.store(cur, std::memory_order_relaxed);
}

JNIEXPORT jboolean JNICALL
Java_com_snes9x_android_Snes9xLib_nativeSaveState(JNIEnv *env, jobject, jstring jPath)
{
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    bool8 ok = S9xFreezeGame(path);
    env->ReleaseStringUTFChars(jPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_snes9x_android_Snes9xLib_nativeLoadState(JNIEnv *env, jobject, jstring jPath)
{
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    bool8 ok = S9xUnfreezeGame(path);
    env->ReleaseStringUTFChars(jPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_snes9x_android_Snes9xLib_nativeShutdown(JNIEnv *, jobject)
{
    Memory.SaveSRAM(S9xGetFilename(".srm", SRAM_DIR).c_str());
    S9xDeinitAPU();
    Memory.Deinit();
    S9xGraphicsDeinit();
    S9xUnmapAllControls();
    {
        std::lock_guard<std::mutex> lock(g_audioMutex);
        g_audioBuffer.clear();
    }
    g_buttons.store(0);
    g_initialized = false;
    LOGI("Emulator shutdown complete");
}

} // extern "C"
