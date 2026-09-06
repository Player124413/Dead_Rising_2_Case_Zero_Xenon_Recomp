#pragma once
#include <cstdint>
void Android_Progress(const char* label, float fraction);
void Android_WaitForeground();
void Android_FramePresented();
void Android_MarkStopped();
void Android_MarkPlaying();

[[noreturn]] void Android_Fatal(const char* error);

struct ANativeWindow;
// Caller owns a reference; release it after vkCreateAndroidSurfaceKHR.
ANativeWindow* Android_AcquireWindow();
bool Android_TakeSurfaceChanged();
void Android_InvalidateSurface();
