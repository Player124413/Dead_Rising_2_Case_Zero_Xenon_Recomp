#pragma once
#include "../host/window.h"
// UI thread writes; SDL event thread samples. No JNI/SDL calls under this lock.
void AndroidTouch_Set(uint16_t buttons, float lx, float ly, float rx, float ry,
                      int leftTrigger, int rightTrigger);
HostPadState AndroidTouch_Read();
void AndroidTouch_Clear();
void AndroidTouch_Merge(HostPadState& pad);
