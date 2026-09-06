#include "../host/stfs_extract.h"
#include "../gpu/vulkan_requirements.h"
#include "vulkan_loader.h"
#include <jni.h>
#include <cstdio>
#include <exception>
#include <mutex>
#include <string>
#include <vector>

namespace {
class Utf {
public:
    Utf(JNIEnv* e, jstring s) : e(e), s(s), p(s ? e->GetStringUTFChars(s, nullptr) : nullptr) {}
    ~Utf() { if (p) e->ReleaseStringUTFChars(s, p); }
    JNIEnv* e; jstring s; const char* p;
};
struct Cancelled {};
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_casezero_launcher_NativeSupport_extract(JNIEnv* env, jclass, jstring source,
                                                  jstring target, jobject progress) {
    Utf src(env, source), dst(env, target);
    if (!src.p || !dst.p) return nullptr; // an OOM already raised a Java exception
    auto cls = env->GetObjectClass(progress);
    auto method = env->GetMethodID(cls, "update", "(JJ)Z");
    if (!method) return nullptr;
    std::string error;
    try {
        const bool ok = StfsExtract::Extract(src.p, dst.p, error, [&](uint64_t done, uint64_t total) {
            const bool proceed = env->CallBooleanMethod(progress, method, jlong(done), jlong(total));
            if (env->ExceptionCheck() || !proceed) throw Cancelled{};
        });
        if (ok) return nullptr;
    } catch (const Cancelled&) { error = "Cancelled"; }
      catch (const std::exception& e) { error = e.what(); }
    if (env->ExceptionCheck()) return nullptr;
    return env->NewStringUTF(error.c_str());
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_casezero_launcher_NativeSupport_diagnostic(JNIEnv*, jclass) { return CZ_DIAGNOSTIC; }

// Probe the SYSTEM driver in the launcher. Imported code is never executed just
// by browsing its settings; the runtime probes the selected driver at startup.
extern "C" JNIEXPORT jstring JNICALL
Java_com_casezero_launcher_NativeSupport_probe(JNIEnv* env, jclass) {
    static std::mutex probeMutex;
    std::lock_guard<std::mutex> lock(probeMutex);
    std::string error;
    if (!AndroidVulkan_Load(nullptr, nullptr, nullptr, error))
        return env->NewStringUTF(error.c_str());
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "Case Zero device check";
    app.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; ci.pApplicationInfo = &app;
    VkInstance instance{};
    const VkResult rc = vkCreateInstance(&ci, nullptr, &instance);
    if (rc != VK_SUCCESS) return env->NewStringUTF("Vulkan instance creation failed");
    volkLoadInstance(instance);
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    std::vector<VkPhysicalDevice> devices(count);
    if (count) vkEnumeratePhysicalDevices(instance, &count, devices.data());
    std::string report = "No Vulkan GPU found";
    if (count) {
        VkPhysicalDeviceProperties props{};
        VkPhysicalDeviceFeatures features{};
        vkGetPhysicalDeviceProperties(devices[0], &props);
        vkGetPhysicalDeviceFeatures(devices[0], &features);
        const auto missing = CzVulkanMissingFeatures(devices[0]);
        char version[96];
        snprintf(version, sizeof version, "\nVulkan %u.%u.%u | vendor 0x%04X\n",
            VK_VERSION_MAJOR(props.apiVersion), VK_VERSION_MINOR(props.apiVersion),
            VK_VERSION_PATCH(props.apiVersion), props.vendorID);
        report = std::string(props.deviceName) + version;
        report += missing.empty() ? "Required features: OK\n" : "Missing: " + missing + "\n";
        report += features.textureCompressionBC ? "BC textures: native" : "BC textures: CPU decode (extra RAM)";
    }
    vkDestroyInstance(instance, nullptr);
    return env->NewStringUTF(report.c_str());
}
