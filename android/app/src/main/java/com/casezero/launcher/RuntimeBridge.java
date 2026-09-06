package com.casezero.launcher;

// Loaded by SDLActivity, never from a static launcher initializer.
final class RuntimeBridge {
    static native void touch(int buttons, float lx, float ly, float rx, float ry, int lt, int rt);
    static native void pause(boolean paused);
    static native void quit();
    static native long frames();
    static native String status();
    private RuntimeBridge() {}
}
