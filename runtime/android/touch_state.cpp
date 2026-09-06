#include "touch_state.h"
#include <algorithm>
#include <cmath>
#include <mutex>
namespace {
std::mutex mutex;
HostPadState state{};
int16_t Axis(float v) {
    if (!std::isfinite(v)) return 0;
    return int16_t(std::clamp(v, -1.f, 1.f) * 32767.f);
}
}
void AndroidTouch_Set(uint16_t buttons, float lx, float ly, float rx, float ry, int lt, int rt) {
    std::lock_guard<std::mutex> lock(mutex);
    state.buttons = buttons;
    state.thumbLX = Axis(lx); state.thumbLY = Axis(ly);
    state.thumbRX = Axis(rx); state.thumbRY = Axis(ry);
    state.leftTrigger = uint8_t(std::clamp(lt, 0, 255));
    state.rightTrigger = uint8_t(std::clamp(rt, 0, 255));
}
HostPadState AndroidTouch_Read() { std::lock_guard<std::mutex> lock(mutex); return state; }
void AndroidTouch_Clear() { AndroidTouch_Set(0, 0, 0, 0, 0, 0, 0); }
void AndroidTouch_Merge(HostPadState& pad) {
    const auto touch = AndroidTouch_Read();
    pad.buttons |= touch.buttons;
    pad.leftTrigger = std::max(pad.leftTrigger, touch.leftTrigger);
    pad.rightTrigger = std::max(pad.rightTrigger, touch.rightTrigger);
    auto merge = [](int16_t& dst, int16_t src) {
        if (std::abs(int(src)) > std::abs(int(dst))) dst = src;
    };
    merge(pad.thumbLX, touch.thumbLX); merge(pad.thumbLY, touch.thumbLY);
    merge(pad.thumbRX, touch.thumbRX); merge(pad.thumbRY, touch.thumbRY);
}
