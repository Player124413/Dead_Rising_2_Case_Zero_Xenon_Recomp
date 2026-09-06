#include "vulkan_loader.h"
#include "../gpu/vulkan_api.h"
#include <adrenotools/driver.h>
#include <dlfcn.h>
#include <mutex>

bool AndroidVulkan_Load(const char* libs, const char* dir, const char* name, std::string& error) {
    static std::mutex mutex;
    static void* handle = nullptr;
    std::lock_guard<std::mutex> lock(mutex);
    if (handle) return true;
    if (name && *name) {
        if (!libs || !*libs || !dir || !*dir || std::string(name).find('/') != std::string::npos) {
            error = "Invalid app-private driver path"; return false;
        }
        handle = adrenotools_open_libvulkan(RTLD_NOW | RTLD_LOCAL,
            ADRENOTOOLS_DRIVER_CUSTOM, nullptr, libs, dir, name, nullptr, nullptr);
    } else {
        handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (!handle) {
        const char* e = dlerror();
        error = std::string("Cannot load Vulkan driver: ") + (e ? e : "adrenotools rejected it");
        return false;
    }
    auto proc = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(handle, "vkGetInstanceProcAddr"));
    if (!proc) {
        error = "Driver does not export vkGetInstanceProcAddr";
        dlclose(handle); handle = nullptr; return false;
    }
    volkInitializeCustom(proc);
    return true;
}
