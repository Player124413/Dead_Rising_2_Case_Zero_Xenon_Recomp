#include "bcn_decode.h"
#include <algorithm>
#include <array>
#include <cstring>

namespace Bcn {
namespace {
uint16_t U16(const uint8_t* p) { return uint16_t(p[0]) | uint16_t(p[1]) << 8; }
void Color(uint16_t c, uint8_t* out) {
    const unsigned r = c >> 11, g = (c >> 5) & 63, b = c & 31;
    out[0] = uint8_t((r << 3) | (r >> 2));
    out[1] = uint8_t((g << 2) | (g >> 4));
    out[2] = uint8_t((b << 3) | (b >> 2));
    out[3] = 255;
}
void Alpha(const uint8_t* p, uint8_t* dst, unsigned stride) {
    uint8_t a[8] = {p[0], p[1]};
    if (a[0] > a[1]) {
        for (unsigned i = 1; i <= 6; ++i)
            a[i + 1] = uint8_t(((7 - i) * a[0] + i * a[1]) / 7);
    } else {
        for (unsigned i = 1; i <= 4; ++i)
            a[i + 1] = uint8_t(((5 - i) * a[0] + i * a[1]) / 5);
        a[6] = 0; a[7] = 255;
    }
    uint64_t indices = 0;
    for (unsigned i = 0; i < 6; ++i) indices |= uint64_t(p[2 + i]) << (8 * i);
    for (unsigned i = 0; i < 16; ++i) dst[i * stride] = a[(indices >> (3 * i)) & 7];
}
void Block(Format f, const uint8_t* p, uint8_t* out) {
    std::memset(out, 0, 64);
    for (unsigned i = 0; i < 16; ++i) out[i * 4 + 3] = 255;
    if (f == Format::BC4 || f == Format::BC5) {
        Alpha(p, out, 4);
        if (f == Format::BC5) Alpha(p + 8, out + 1, 4);
        return;
    }
    const uint8_t* c = p + (f == Format::BC1 ? 0 : 8);
    uint8_t palette[4][4]{};
    Color(U16(c), palette[0]); Color(U16(c + 2), palette[1]);
    if (f != Format::BC1 || U16(c) > U16(c + 2)) {
        for (unsigned j = 0; j < 3; ++j) {
            palette[2][j] = uint8_t((2 * palette[0][j] + palette[1][j]) / 3);
            palette[3][j] = uint8_t((palette[0][j] + 2 * palette[1][j]) / 3);
        }
        palette[2][3] = palette[3][3] = 255;
    } else {
        for (unsigned j = 0; j < 3; ++j)
            palette[2][j] = uint8_t((palette[0][j] + palette[1][j]) / 2);
        palette[2][3] = 255; // palette[3] remains transparent black.
    }
    for (unsigned i = 0; i < 16; ++i)
        std::memcpy(out + i * 4, palette[(c[4 + i / 4] >> (2 * (i % 4))) & 3], 4);
    if (f == Format::BC2)
        for (unsigned i = 0; i < 16; ++i)
            out[i * 4 + 3] = uint8_t(((p[i / 2] >> ((i % 2) * 4)) & 15) * 17);
    if (f == Format::BC3) Alpha(p, out + 3, 4);
}
}
bool Decode(Format f, const uint8_t* src, size_t size, uint32_t w,
            uint32_t h, std::vector<uint8_t>& rgba) {
    if (!src || !w || !h || w > 8192 || h > 8192) return false;
    if (f < Format::BC1 || f > Format::BC5) return false;
    const size_t bytes = (f == Format::BC1 || f == Format::BC4) ? 8 : 16;
    const size_t bw = (w + 3) / 4, bh = (h + 3) / 4;
    if (size != bw * bh * bytes) return false;
    rgba.resize(size_t(w) * h * 4);
    uint8_t block[64];
    for (size_t y = 0; y < bh; ++y) for (size_t x = 0; x < bw; ++x) {
        Block(f, src + (y * bw + x) * bytes, block);
        for (unsigned by = 0; by < 4 && y * 4 + by < h; ++by)
            for (unsigned bx = 0; bx < 4 && x * 4 + bx < w; ++bx)
                std::memcpy(rgba.data() + ((y * 4 + by) * w + x * 4 + bx) * 4,
                            block + (by * 4 + bx) * 4, 4);
    }
    return true;
}
}
