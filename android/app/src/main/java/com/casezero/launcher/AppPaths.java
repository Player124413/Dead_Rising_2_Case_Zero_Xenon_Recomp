package com.casezero.launcher;

import android.content.Context;
import java.io.*;
import java.nio.channels.*;

final class AppPaths {
    final File files, runtime, assets, game, saves, logs, driver, stage;
    AppPaths(Context context) {
        files = context.getFilesDir(); runtime = new File(files, "runtime"); assets = new File(runtime, "assets");
        game = new File(assets, "game"); saves = new File(files, "saves"); logs = new File(files, "logs");
        driver = new File(files, "driver"); stage = new File(files, "import-tmp");
    }
    void init() throws IOException {
        for (File dir : new File[]{files, assets, logs}) SafeFiles.mkdir(dir);
    }
    void recover() throws IOException {
        SafeFiles.recoverDirectory(game); SafeFiles.recoverDirectory(saves); SafeFiles.recoverDirectory(driver);
    }
    Lock lock() throws IOException { return new Lock(new File(files, "session.lock")); }
    static final class Lock implements AutoCloseable {
        private final RandomAccessFile file;
        private final FileLock lock;
        Lock(File path) throws IOException {
            file = new RandomAccessFile(path, "rw");
            FileLock acquired;
            try { acquired = file.getChannel().tryLock(); }
            catch (OverlappingFileLockException e) { acquired = null; }
            if (acquired == null) { file.close(); throw new IOException("A transfer or game session is already active"); }
            lock = acquired;
        }
        public void close() throws IOException { lock.release(); file.close(); }
    }
}
