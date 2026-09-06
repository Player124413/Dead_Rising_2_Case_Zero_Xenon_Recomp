#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

// Portable BC1..BC5 fallback. Input is already untiled/endian-corrected. A face
// or mip is decoded independently; dimensions need not be a multiple of four.
namespace Bcn {
enum class Format { BC1, BC2, BC3, BC4, BC5 };
bool Decode(Format format, const uint8_t* src, size_t size, uint32_t width,
            uint32_t height, std::vector<uint8_t>& rgba);
}
