#pragma once
#include <cstdint>
void Android_Progress(const char* label, float fraction);
void Android_WaitForeground();
void Android_FramePresented();
void Android_MarkStopped();
void Android_MarkPlaying();
