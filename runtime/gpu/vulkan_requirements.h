#pragma once
#include "vulkan_api.h"
#include <algorithm>
#include <string>

// These are the features actually used by the translated shaders and the five
// update-after-bind heaps. Vulkan 1.3 alone is NOT a sufficient compatibility test.
inline uint32_t CzVulkanDescriptorCapacity(VkPhysicalDevice device) {
    VkPhysicalDeviceVulkan12Properties v12{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_PROPERTIES};
    VkPhysicalDeviceProperties2 props{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
    props.pNext = &v12;
    vkGetPhysicalDeviceProperties2(device, &props);
    return std::min({v12.maxPerStageDescriptorUpdateAfterBindSampledImages / 4,
        v12.maxDescriptorSetUpdateAfterBindSampledImages / 4,
        v12.maxPerStageDescriptorUpdateAfterBindSamplers,
        v12.maxDescriptorSetUpdateAfterBindSamplers,
        v12.maxUpdateAfterBindDescriptorsInAllPools / 5,
        v12.maxPerStageUpdateAfterBindResources / 5});
}
inline std::string CzVulkanMissingFeatures(VkPhysicalDevice device) {
    VkPhysicalDeviceProperties props{};
    vkGetPhysicalDeviceProperties(device, &props);
    if (props.apiVersion < VK_API_VERSION_1_3) return "Vulkan 1.3";
    VkPhysicalDeviceVulkan12Features v12{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
    VkPhysicalDeviceVulkan13Features v13{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
    VkPhysicalDeviceFeatures2 f{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
    f.pNext = &v13; v13.pNext = &v12;
    vkGetPhysicalDeviceFeatures2(device, &f);
    std::string missing;
    auto need = [&](bool value, const char* name) {
        if (!value) { if (!missing.empty()) missing += ", "; missing += name; }
    };
    need(f.features.shaderInt64, "shaderInt64");
    need(f.features.independentBlend, "independentBlend");
    need(v12.bufferDeviceAddress, "bufferDeviceAddress");
    need(v12.descriptorIndexing, "descriptorIndexing");
    need(v12.runtimeDescriptorArray, "runtimeDescriptorArray");
    need(v12.descriptorBindingPartiallyBound, "descriptorBindingPartiallyBound");
    need(v12.descriptorBindingUpdateUnusedWhilePending, "descriptorBindingUpdateUnusedWhilePending");
    need(v12.descriptorBindingSampledImageUpdateAfterBind, "descriptorBindingSampledImageUpdateAfterBind");
    need(v12.descriptorBindingVariableDescriptorCount, "descriptorBindingVariableDescriptorCount");
    need(v12.shaderSampledImageArrayNonUniformIndexing, "shaderSampledImageArrayNonUniformIndexing");
    need(v13.dynamicRendering, "dynamicRendering");
    need(props.limits.maxBoundDescriptorSets >= 5, "5 descriptor sets");
    need(CzVulkanDescriptorCapacity(device) >= 256, "256 descriptors per heap");
    return missing;
}
