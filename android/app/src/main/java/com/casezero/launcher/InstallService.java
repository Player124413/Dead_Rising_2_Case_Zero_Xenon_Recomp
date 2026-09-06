package com.casezero.launcher;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Long transfers run in a foreground service, not on the Activity/UI thread. */
public final class InstallService extends Service {
    static final String PACKAGE = "package", ZIP = "zip", FOLDER = "folder", DRIVER = "driver",
        BACKUP = "backup", RESTORE = "restore", CLEAR = "clear";
    interface Listener { void changed(boolean active, long bytes, String result); }
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile boolean active;
    private static volatile long bytes;
    private static volatile String result = "";
    private static final AtomicBoolean cancel = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastProgress;
    static void observe(Listener listener) { listeners.add(listener); listener.changed(active, bytes, result); }
    static void unobserve(Listener listener) { listeners.remove(listener); }
    static boolean busy() { return active; }
    static void cancel() { cancel.set(true); }
    static void begin(Context context, String action, Uri uri) {
        Intent intent = new Intent(context, InstallService.class).setAction(action).setData(uri);
        intent.addFlags(BACKUP.equals(action) ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startForegroundService(intent);
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel("transfers", getString(R.string.transfer_channel), NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, LauncherActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, InstallService.class).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "transfers").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.working))
            .setContentIntent(open).setOngoing(true).setProgress(0, 0, true)
            .addAction(new Notification.Action.Builder(null, getString(R.string.cancel), stop).build()).build();
    }
    @Override public synchronized int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null) { stopSelf(id); return START_NOT_STICKY; }
        if ("cancel".equals(intent.getAction())) { cancel(); return START_NOT_STICKY; }
        if (active) return START_NOT_STICKY;
        active = true; bytes = 0; result = ""; cancel.set(false);
        startForeground(11, notification()); publish();
        worker.execute(() -> {
            try { execute(intent.getAction(), intent.getData()); result = getString(R.string.complete); }
            catch (InterruptedIOException e) { result = getString(R.string.cancelled); }
            catch (Exception | LinkageError e) { Log.e("CaseZero", "Transfer failed", e); result = getString(R.string.error) + ": " + e.getMessage(); }
            finally {
                // Only grants taken by the launcher for this transfer, never a broad
                // external-storage permission. Transient grants expire with the task.
                if (intent.getData() != null) {
                    for (android.content.UriPermission grant : getContentResolver().getPersistedUriPermissions()) {
                        if (!grant.getUri().equals(intent.getData())) continue;
                        int granted = (grant.isReadPermission() ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0) |
                            (grant.isWritePermission() ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
                        try { getContentResolver().releasePersistableUriPermission(grant.getUri(), granted); }
                        catch (SecurityException ignored) { /* provider revoked it first */ }
                    }
                }
                active = false;
                main.post(() -> { publish(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); });
            }
        });
        return START_NOT_STICKY; // Interrupted transactions are recovered on next launch, not blindly replayed.
    }
    private void publish() { for (Listener listener : listeners) listener.changed(active, bytes, result); }
    private void progress(long value) throws IOException {
        if (cancel.get()) throw new InterruptedIOException("Cancelled");
        bytes = value;
        long now = SystemClock.elapsedRealtime();
        if (now - lastProgress > 150) { lastProgress = now; main.post(this::publish); }
    }
    private InputStream input(Uri uri) throws IOException {
        if (uri == null) throw new IOException("No document selected");
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Document provider returned no stream");
        return in;
    }
    private void execute(String action, Uri uri) throws Exception {
        AppPaths p = new AppPaths(this); p.init();
        try (AppPaths.Lock ignored = p.lock()) {
            p.recover(); SafeFiles.mkdir(p.saves);
            if (CLEAR.equals(action)) { clearCaches(p); return; }
            if (BACKUP.equals(action)) {
                OutputStream out = getContentResolver().openOutputStream(Objects.requireNonNull(uri), "wt");
                if (out == null) throw new IOException("Cannot open destination");
                SafeFiles.zipDirectory(p.saves, out, this::progress); return;
            }
            SafeFiles.deleteTree(p.stage); SafeFiles.mkdir(p.stage);
            try {
                File incoming = new File(p.stage, "incoming"); SafeFiles.mkdir(incoming);
                SafeFiles.Budget budget = new SafeFiles.Budget(4L * 1024 * 1024 * 1024, 50000, this::progress);
                if (PACKAGE.equals(action)) {
                    File packageFile = new File(p.stage, "source.stfs");
                    try (InputStream in = input(uri)) { SafeFiles.copy(in, packageFile, budget); }
                    GameFiles.validatePackage(packageFile);
                    String error = NativeSupport.extract(packageFile.getPath(), incoming.getPath(), (done, total) -> {
                        try { progress(done); return true; } catch (IOException e) { return false; }
                    });
                    progress(bytes);
                    if (error != null) throw new IOException(error);
                } else if (ZIP.equals(action) || RESTORE.equals(action)) {
                    if (RESTORE.equals(action)) budget = new SafeFiles.Budget(256L * 1024 * 1024, 4096, this::progress);
                    try (InputStream in = input(uri)) { SafeFiles.unzip(in, incoming, budget); }
                } else if (FOLDER.equals(action)) {
                    String doc = DocumentsContract.getTreeDocumentId(Objects.requireNonNull(uri));
                    copyTree(uri, doc, incoming, 0, budget, new HashSet<>());
                } else if (DRIVER.equals(action)) { installDriver(uri, p, incoming); return; }
                else throw new IOException("Unknown transfer action");
                progress(bytes); // Cancellation always precedes the atomic commit.
                if (RESTORE.equals(action)) {
                    validateSaves(incoming);
                    File settings = new File(p.saves, "cz_settings.txt");
                    if (settings.isFile()) Files.copy(settings.toPath(), new File(incoming, "cz_settings.txt").toPath());
                    SafeFiles.replaceDirectory(incoming, p.saves);
                } else {
                    File root = GameFiles.findRoot(incoming);
                    // Invalidate derived data before commit. A cleanup failure
                    // leaves the old game intact and cannot masquerade as an
                    // unsuccessful import AFTER the replacement succeeded.
                    clearCaches(p);
                    SafeFiles.deleteTree(new File(p.assets, "game_patched"));
                    SafeFiles.deleteTree(new File(p.assets, "game_kbm"));
                    progress(bytes);
                    SafeFiles.replaceDirectory(root, p.game);
                }
            } finally {
                try { SafeFiles.deleteTree(p.stage); }
                catch (IOException e) { Log.w("CaseZero", "Staging cleanup deferred to next transfer", e); }
            }
        }
    }
    private void clearCaches(AppPaths p) throws IOException {
        SafeFiles.deleteTree(new File(p.assets, "shader_spv"));
        SafeFiles.deleteTree(new File(p.files, "cache"));
        // Native pipeline/golden caches and temporary files; saves are separate.
    }
    private void copyTree(Uri tree, String id, File dst, int depth, SafeFiles.Budget budget, Set<String> names) throws IOException {
        if (depth > 32) throw new IOException("Folder is nested too deeply");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(children, cols, null, null, null)) {
            if (cursor == null) throw new IOException("Cannot enumerate selected folder");
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name == null || name.contains("/")) throw new IOException("Invalid document name");
                File target = SafeFiles.resolve(dst, name);
                if (!names.add(target.getCanonicalPath().toLowerCase(Locale.ROOT))) throw new IOException("Duplicate case-insensitive folder entry: " + name);
                String child = cursor.getString(0);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    budget.file(); budget.add(0); SafeFiles.mkdir(target); copyTree(tree, child, target, depth + 1, budget, names);
                } else try (InputStream in = input(DocumentsContract.buildDocumentUriUsingTree(tree, child))) {
                    SafeFiles.copy(in, target, budget);
                }
            }
        }
    }
    private static void validateSaves(File root) throws IOException {
        int count = 0;
        for (File f : Objects.requireNonNull(root.listFiles())) {
            if (!f.isDirectory()) throw new IOException("Save backups contain save-container directories only");
            for (File data : Objects.requireNonNull(f.listFiles())) {
                if (!data.isFile() || !data.getName().toLowerCase(Locale.ROOT).endsWith(".dsf") || data.length() == 0)
                    throw new IOException("Not a Case Zero save container: " + f.getName());
                ++count;
            }
        }
        if (count == 0) throw new IOException("Backup contains no .DSF save files");
    }
    private void installDriver(Uri uri, AppPaths p, File incoming) throws Exception {
        SafeFiles.Budget budget = new SafeFiles.Budget(128L * 1024 * 1024, 128, this::progress);
        File raw = new File(p.stage, "driver-input");
        try (InputStream in = input(uri)) { SafeFiles.copy(in, raw, budget); }
        byte[] magic = new byte[4];
        try (DataInputStream in = new DataInputStream(new FileInputStream(raw))) { in.readFully(magic); }
        File so;
        if (magic[0] == 'P' && magic[1] == 'K') {
            budget = new SafeFiles.Budget(128L * 1024 * 1024, 128, this::progress);
            try (InputStream in = new FileInputStream(raw)) { SafeFiles.unzip(in, incoming, budget); }
            File metadata = new File(incoming, "meta.json");
            if (!metadata.isFile() || metadata.length() > 65536) throw new IOException("Driver ZIP needs a root meta.json");
            JSONObject meta = new JSONObject(SafeFiles.readText(metadata, 65536));
            String name = meta.getString("libraryName");
            if (name.contains("/") || name.contains("\\")) throw new IOException("Driver library must be at the ZIP root");
            if (meta.optInt("minApi", 0) > Build.VERSION.SDK_INT) throw new IOException("Driver requires a newer Android version");
            so = SafeFiles.resolve(incoming, name);
        } else { so = new File(incoming, "driver.so"); Files.copy(raw.toPath(), so.toPath()); }
        try (RandomAccessFile file = new RandomAccessFile(so, "r")) {
            byte[] hdr = new byte[20]; file.readFully(hdr);
            if (hdr[0] != 0x7f || hdr[1] != 'E' || hdr[2] != 'L' || hdr[3] != 'F' || hdr[4] != 2 || hdr[5] != 1 ||
                hdr[16] != 3 || hdr[17] != 0 || (hdr[18] & 255) != 183 || hdr[19] != 0)
                throw new IOException("Driver must be an ELF64 little-endian AArch64 shared library");
        }
        File dst = new File(incoming, "driver.so");
        if (!so.equals(dst) && !so.renameTo(dst)) throw new IOException("Cannot stage driver library");
        // Import does not enable or execute the library. The user opts in separately.
        File enabled = new File(incoming, "enabled");
        if (enabled.exists()) SafeFiles.deleteTree(enabled);
        progress(bytes); SafeFiles.replaceDirectory(incoming, p.driver);
    }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
