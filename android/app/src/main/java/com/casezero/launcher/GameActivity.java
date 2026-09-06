package com.casezero.launcher;

import org.libsdl.app.SDLActivity;
import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;

public final class GameActivity extends SDLActivity {
    private volatile AppPaths.Lock session;
    private TouchControlsView touch;
    private TextView info, stats;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastTime, lastFrames;
    private boolean nativeLoaded;
    @Override public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        // SDL 2.32 passes this Activity as HIDDeviceManager's Context. Its
        // two-argument receiver registration needs explicit flags at target 35.
        // Only protected Bluetooth ACL broadcasts are exported; the mixed USB
        // filter includes our private PendingIntent and MUST NOT be exported.
        if (Build.VERSION.SDK_INT >= 33) {
            boolean protectedBluetooth = filter.countActions() > 0;
            for (int i = 0; i < filter.countActions(); ++i) {
                String action = filter.getAction(i);
                protectedBluetooth &= android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                    || android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);
            }
            return super.registerReceiver(receiver, filter,
                protectedBluetooth ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED);
        }
        return super.registerReceiver(receiver, filter);
    }
    @Override protected String[] getLibraries() { return new String[]{"SDL2", "main"}; }
    @Override protected String[] getArguments() {
        // This runs on SDL's native-start thread, before SDL_main. Preparation
        // and the process lease cannot block Android's UI thread.
        boolean smoke = getIntent().getBooleanExtra("smoke", false);
        String mode = smoke ? "--android-smoke" : "--android";
        try {
            AppPaths paths = new AppPaths(this); paths.init(); session = paths.lock(); paths.recover();
            SafeFiles.mkdir(paths.saves);
            if (!smoke) { GameFiles.validate(paths.game); copyAssets("support", paths.runtime); }
        } catch (IOException e) {
            mode = "--android-abort"; // SDL_main refuses this before accessing game memory.
            android.util.Log.e("CaseZero", "Cannot start runtime", e);
            try { SafeFiles.atomicText(new File(getFilesDir(), "last-session.txt"), "failed: " + e.getMessage()); }
            catch (IOException ignored) { /* original error remains in logcat */ }
        }
        return new String[]{mode, getFilesDir().getAbsolutePath(), getApplicationInfo().nativeLibraryDir};
    }
    @Override public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        // SDL's generic resizable-window policy otherwise overrides the manifest
        // and lets a portrait-locked phone rotate this landscape game.
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (mLayout == null) return; // SDL displays its native-library load error itself.
        nativeLoaded = true;
        mSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            private void update(SurfaceHolder holder) {
                Surface surface = holder.getSurface();
                RuntimeBridge.surface(surface != null && surface.isValid() ? surface : null);
            }
            @Override public void surfaceCreated(SurfaceHolder holder) { update(holder); }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { update(holder); }
            @Override public void surfaceDestroyed(SurfaceHolder holder) { RuntimeBridge.surface(null); }
        });
        if (mSurface.getHolder().getSurface().isValid()) RuntimeBridge.surface(mSurface.getHolder().getSurface());
        FrameLayout overlays = new FrameLayout(this);
        touch = new TouchControlsView(this, false, RuntimeBridge::touch);
        overlays.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        info = Ui.text(this, getString(R.string.working), 16, Ui.WHITE);
        info.setGravity(Gravity.CENTER); info.setBackgroundColor(0xdd101316);
        overlays.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        stats = Ui.text(this, "", 12, Ui.ACCENT); stats.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, 0);
        overlays.addView(stats, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        Button exit = new Button(this); exit.setText("Ⅱ"); exit.setContentDescription(getString(R.string.stop_game));
        exit.setOnClickListener(v -> onBackPressed());
        overlays.addView(exit, new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 48), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        mLayout.addView(overlays, new android.widget.RelativeLayout.LayoutParams(-1, -1));
        overlays.setOnApplyWindowInsetsListener((v, insets) -> {
            DisplayCutout cutout = insets.getDisplayCutout();
            int left = cutout == null ? 0 : cutout.getSafeInsetLeft();
            int right = cutout == null ? 0 : cutout.getSafeInsetRight();
            v.setPadding(Math.max(left, insets.getSystemWindowInsetLeft()), insets.getSystemWindowInsetTop(),
                Math.max(right, insets.getSystemWindowInsetRight()), insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lastTime = SystemClock.elapsedRealtime(); handler.post(poll);
    }
    private void copyAssets(String from, File to) throws IOException {
        String[] children = getAssets().list(from);
        if (children == null) throw new IOException("Missing support assets");
        SafeFiles.mkdir(to);
        for (String name : children) {
            String source = from + "/" + name;
            String[] nested = getAssets().list(source);
            File dst = SafeFiles.resolve(to, name);
            if (nested != null && nested.length > 0) copyAssets(source, dst);
            else {
                try (InputStream in = getAssets().open(source)) {
                    byte[] bytes = SafeFiles.readLimited(in, 2 * 1024 * 1024);
                    // These are byte blobs (not text). Stage and rename atomically.
                    File tmp = new File(dst.getPath() + ".tmp");
                    try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(bytes); }
                    java.nio.file.Files.move(tmp.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!nativeLoaded || isFinishing()) return;
            String status = RuntimeBridge.status();
            int split = status.indexOf('\n');
            String label = split >= 0 ? status.substring(0, split) : status;
            info.setText(label); info.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
            long now = SystemClock.elapsedRealtime(), frames = RuntimeBridge.frames();
            if (now - lastTime >= 500) {
                if (getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("show_fps", false))
                    stats.setText(String.format(Locale.ROOT, "%.1f FPS · %d MB PSS", (frames - lastFrames) * 1000.0 / (now - lastTime), Debug.getPss() / 1024));
                else stats.setText("");
                lastTime = now; lastFrames = frames;
            }
            handler.postDelayed(this, 250);
        }
    };
    @Override protected void onPause() {
        handler.removeCallbacks(poll);
        if (nativeLoaded) { touch.release(); RuntimeBridge.pause(true); }
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (nativeLoaded) {
            RuntimeBridge.pause(false);
            lastTime = SystemClock.elapsedRealtime(); lastFrames = RuntimeBridge.frames();
            handler.removeCallbacks(poll); handler.postDelayed(poll, 250);
        }
    }
    @Override public void onBackPressed() {
        if (!nativeLoaded) { super.onBackPressed(); return; }
        touch.release();
        new AlertDialog.Builder(this).setTitle(R.string.stop_game).setMessage(R.string.stop_game_hint)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.continue_action, (d, w) -> {
                RuntimeBridge.quit(); finish();
            }).show();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (touch != null && e.getActionMasked() == MotionEvent.ACTION_DOWN) touch.touchDevice();
        return super.dispatchTouchEvent(e);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (touch != null && e.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            (Math.abs(e.getAxisValue(MotionEvent.AXIS_X)) > .25f || Math.abs(e.getAxisValue(MotionEvent.AXIS_Y)) > .25f ||
             Math.abs(e.getAxisValue(MotionEvent.AXIS_Z)) > .25f || Math.abs(e.getAxisValue(MotionEvent.AXIS_RZ)) > .25f)) touch.physicalController();
        return super.dispatchGenericMotionEvent(e);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (touch != null && e.getAction() == KeyEvent.ACTION_DOWN && e.isFromSource(InputDevice.SOURCE_GAMEPAD)) touch.physicalController();
        return super.dispatchKeyEvent(e);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (nativeLoaded) { RuntimeBridge.quit(); touch.release(); }
        // SDL joins its native thread here. A shader compiler / stalled driver
        // must not keep Android's UI thread blocked indefinitely during teardown.
        // Give normal shutdown a chance, then terminate ONLY the :runtime process.
        java.util.concurrent.ScheduledExecutorService exitGuard = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        exitGuard.schedule(() -> android.os.Process.killProcess(android.os.Process.myPid()), 2, java.util.concurrent.TimeUnit.SECONDS);
        super.onDestroy();
        exitGuard.shutdownNow();
        if (session != null) try { session.close(); } catch (IOException e) { android.util.Log.w("CaseZero", "Session lock", e); }
        // The runtime contains process-lifetime guest threads and globals. Never
        // reuse this process for a second SDL session after a normal native return.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
