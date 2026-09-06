package com.casezero.launcher;

final class NativeSupport {
    static { System.loadLibrary("cz_support"); }
    interface Progress { boolean update(long done, long total); }
    static native String extract(String source, String target, Progress progress);
    static native String probe();
    static native boolean diagnostic();
    private NativeSupport() {}
}
