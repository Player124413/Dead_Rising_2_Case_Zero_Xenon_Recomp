#include "runtime_bridge.h"
#include "touch_state.h"
#include "vulkan_loader.h"
#include "../gpu/vk_renderer.h"
#include "../host/window.h"
#include <jni.h>
#include <atomic>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <sstream>
#include <string>
#include <unistd.h>

int CzRuntimeMain(int argc, char** argv);
namespace {
std::mutex statusMutex, pauseMutex;
std::condition_variable pauseChanged;
bool paused = false;
std::string status = "Starting";
std::filesystem::path files;
std::atomic<uint64_t> frames{0};
void Session(const char* state) {
    if (files.empty()) return;
    std::ofstream out(files / "last-session.txt", std::ios::trunc);
    out << state << '\n';
}
void Env(const char* key, const std::string& value) { setenv(key, value.c_str(), 1); }
}
void Android_Progress(const char* label, float fraction) {
    std::lock_guard<std::mutex> lock(statusMutex);
    status = std::string(label ? label : "") + "\n" + std::to_string(fraction);
}
void Android_FramePresented() { frames.fetch_add(1, std::memory_order_relaxed); }
void Android_MarkStopped() { Session("stopped"); }
void Android_MarkPlaying() { Session("running"); Android_Progress("", 1.f); }
void Android_WaitForeground() {
    std::unique_lock<std::mutex> lock(pauseMutex);
    pauseChanged.wait(lock, [] { return !paused; });
}
extern "C" JNIEXPORT void JNICALL
Java_com_casezero_launcher_RuntimeBridge_touch(JNIEnv*, jclass, jint buttons,
    jfloat lx, jfloat ly, jfloat rx, jfloat ry, jint lt, jint rt) {
    AndroidTouch_Set(uint16_t(buttons), lx, ly, rx, ry, lt, rt);
}
extern "C" JNIEXPORT void JNICALL
Java_com_casezero_launcher_RuntimeBridge_pause(JNIEnv*, jclass, jboolean value) {
    AndroidTouch_Clear();
    { std::lock_guard<std::mutex> lock(pauseMutex); paused = value; }
    pauseChanged.notify_all();
    if (!value) VkRenderer_RequestSwapchainRebuild();
}
extern "C" JNIEXPORT void JNICALL
Java_com_casezero_launcher_RuntimeBridge_quit(JNIEnv*, jclass) {
    { std::lock_guard<std::mutex> lock(pauseMutex); paused = false; }
    pauseChanged.notify_all();
    AndroidTouch_Clear();
    Host_RequestQuit("Return to Android launcher");
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_casezero_launcher_RuntimeBridge_frames(JNIEnv*, jclass) {
    return jlong(frames.load(std::memory_order_relaxed));
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_casezero_launcher_RuntimeBridge_status(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(statusMutex);
    return env->NewStringUTF(status.c_str());
}

// SDLActivity calls this on SDL's main thread. All paths come from Android's
// Context, not /proc/self/exe or a content:// URI. The Activity lives in :runtime
// so the desktop runtime's intentional _Exit never kills the launcher.
extern "C" __attribute__((visibility("default"))) int SDL_main(int argc, char** argv) {
    if (argc != 4 || (std::string(argv[1]) != "--android" && std::string(argv[1]) != "--android-smoke")) return 2;
    files = argv[2];
    const std::string libs = argv[3];
    if (!files.is_absolute() || !std::filesystem::is_directory(files)) return 2;
    std::error_code ec;
    std::filesystem::create_directories(files / "logs", ec);
    const auto log = files / "logs/runtime.log", previous = files / "logs/previous.log";
    std::filesystem::rename(log, previous, ec);
    if (!freopen(log.c_str(), "w", stderr)) return 1;
    if (dup2(fileno(stderr), STDOUT_FILENO) < 0) return 1;
    setvbuf(stderr, nullptr, _IONBF, 0);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    Env("CZ_ROOT", (files / "runtime").string());
    Env("CZ_SAVE_DIR", (files / "saves").string());
    Env("CZ_ANDROID_LIBRARY_DIR", libs);
    Env("CZ_DXC_LIB", libs + "/libdxcompiler.so");
    Env("CZ_VKDRAW", "1"); Env("CZ_LAUNCHER", "0");
    Env("CZ_VK_RT", "0"); Env("CZ_VK_RT_SHADOWS", "0");
    Env("SDL_TOUCH_MOUSE_EVENTS", "0"); Env("SDL_MOUSE_TOUCH_EVENTS", "0");
    Env("SDL_ANDROID_TRAP_BACK_BUTTON", "1");
    Env("SDL_ANDROID_BLOCK_ON_PAUSE", "0"); // native GPU pause checkpoint is independent of SDL's pump
    Env("SDL_VIDEO_MINIMIZE_ON_FOCUS_LOSS", "0");
    Env("CZ_VK_MSAA", "1"); // one sample = no multisampling
    std::ifstream settings(files / "android.env");
    std::string line;
    while (std::getline(settings, line)) {
        const auto at = line.find('=');
        if (at == std::string::npos) continue;
        const auto key = line.substr(0, at), value = line.substr(at + 1);
        if (key == "CZ_VK_MSAA" && (value == "1" || value == "2")) Env(key.c_str(), value);
    }
    // Relative developer diagnostics belong in app-private storage too.
    if (chdir(files.c_str()) != 0) return 1;
    if (std::string(argv[1]) == "--android-smoke") {
        char program[] = "cz_runtime", smoke[] = "--smoke";
        char* arguments[] = {program, smoke, nullptr};
        const int rc = CzRuntimeMain(2, arguments);
        Session(rc == 0 ? "smoke-ok (stub, not gameplay)" : "smoke-failed");
        return rc;
    }
    Session("starting");
    const auto driver = files / "driver";
    const bool custom = std::filesystem::exists(driver / "enabled");
    std::string error;
    if (!AndroidVulkan_Load(libs.c_str(), driver.c_str(), custom ? "driver.so" : "", error)) {
        fprintf(stderr, "[android] %s\n", error.c_str());
        Session("driver-failed"); return 1;
    }
    char program[] = "cz_runtime";
    char* arguments[] = {program, nullptr};
    const int rc = CzRuntimeMain(1, arguments);
    Session(rc == 0 ? "stopped" : "failed");
    return rc;
}
