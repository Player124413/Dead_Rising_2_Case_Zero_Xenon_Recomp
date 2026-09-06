#pragma once
#include <string>
// A custom loader is process-lifetime state. Never mix its dispatch table with
// SDL's system loader, and never fall back silently after a custom-driver error.
bool AndroidVulkan_Load(const char* nativeLibDir, const char* driverDir,
                        const char* driverName, std::string& error);
