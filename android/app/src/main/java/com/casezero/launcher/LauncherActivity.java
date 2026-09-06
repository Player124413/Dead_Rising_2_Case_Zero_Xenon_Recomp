package com.casezero.launcher;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class LauncherActivity extends Activity implements InstallService.Listener {
    private static final int PICK_PACKAGE = 101, PICK_ZIP = 102, PICK_FOLDER = 103, PICK_DRIVER = 104, EXPORT_SAVES = 105, RESTORE_SAVES = 106;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AppPaths paths;
    private GraphicsSettings settings;
    private LinearLayout content;
    private TextView gameStatus, transferStatus, gpuStatus, sessionStatus;
    private ProgressBar progress;
    private Button play, cancel;
    private String gpu = "";
    private boolean libraries;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        paths = new AppPaths(this);
        try {
            paths.init();
            if (!InstallService.busy()) try (AppPaths.Lock ignored = paths.lock()) { paths.recover(); SafeFiles.mkdir(paths.saves); }
            settings = new GraphicsSettings(this);
            libraries = NativeSupport.diagnostic() == BuildConfig.DIAGNOSTIC;
        } catch (Exception | LinkageError e) {
            libraries = false;
            try { settings = new GraphicsSettings(this); } catch (IOException ignored) { /* storage error UI below */ }
            Ui.error(this, e);
        }
        if (settings == null) { TextView error = Ui.text(this, getString(R.string.error), 20, Ui.WHITE); setContentView(error); return; }
        buildUi();
        if (!libraries) Ui.error(this, new IOException(getString(R.string.native_failed)));
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setFitsSystemWindows(true);
        content = Ui.column(this); int pad = Ui.dp(this, 20); content.setPadding(pad, Ui.dp(this, 24), pad, Ui.dp(this, 32));
        scroll.addView(content); setContentView(scroll);
        TextView title = Ui.text(this, "DEAD RISING 2", 30, Ui.WHITE); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setLetterSpacing(.035f); content.addView(title);
        TextView sub = Ui.text(this, "CASE ZERO", 42, Ui.ACCENT); sub.setTypeface(Typeface.DEFAULT, Typeface.BOLD); sub.setLetterSpacing(.08f); content.addView(sub);
        content.addView(Ui.text(this, getString(R.string.subtitle), 15, Ui.MUTED));
        content.addView(Ui.text(this, getString(R.string.experimental), 10, Ui.ACCENT));
        content.addView(Ui.text(this, getString(BuildConfig.DIAGNOSTIC ? R.string.diagnostic_notice : R.string.disclaimer), 14, Ui.MUTED));
        LinearLayout files = Ui.card(this, content, R.string.game_files);
        gameStatus = Ui.text(this, "", 15, Ui.WHITE); files.addView(gameStatus);
        files.addView(Ui.text(this, getString(R.string.source_hint), 13, Ui.MUTED));
        Ui.button(this, files, R.string.install_package, () -> pick(PICK_PACKAGE));
        Ui.button(this, files, R.string.install_zip, () -> pick(PICK_ZIP));
        Ui.button(this, files, R.string.install_folder, () -> pick(PICK_FOLDER));
        play = Ui.button(this, files, BuildConfig.DIAGNOSTIC ? R.string.smoke : R.string.play, this::launch);
        play.setTextColor(Ui.INK); play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        play.setBackgroundTintList(ColorStateList.valueOf(Ui.ACCENT));
        transferStatus = Ui.text(this, "", 13, Ui.MUTED); files.addView(transferStatus);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); files.addView(progress);
        cancel = Ui.button(this, files, R.string.cancel, InstallService::cancel);
        LinearLayout graphics = Ui.card(this, content, R.string.graphics);
        List<int[]> resolutions = new ArrayList<>(Arrays.asList(new int[]{1280,720}, new int[]{1600,900}, new int[]{1920,1080}));
        int selected = -1;
        for (int i = 0; i < resolutions.size(); ++i) if (resolutions.get(i)[0] == settings.width && resolutions.get(i)[1] == settings.height) selected = i;
        if (selected < 0) { selected = resolutions.size(); resolutions.add(new int[]{settings.width, settings.height}); }
        String[] names = new String[resolutions.size()];
        for (int i = 0; i < names.length; ++i) names[i] = resolutions.get(i)[0] + " × " + resolutions.get(i)[1];
        Ui.choose(this, graphics, R.string.resolution, names, selected, n -> {
            settings.width = resolutions.get(n)[0]; settings.height = resolutions.get(n)[1]; saveSettings();
        });
        final int[] caps = {30,60,90,120,240,480,0}; int capAt = 0;
        for (int i = 0; i < caps.length; ++i) if (caps[i] == settings.fps) capAt = i;
        Ui.choose(this, graphics, R.string.fps_cap, new String[]{"30","60","90","120","240","480","∞"}, capAt, n -> { settings.fps = caps[n]; saveSettings(); });
        Ui.choose(this, graphics, R.string.shadow, new String[]{getString(R.string.low),getString(R.string.medium),getString(R.string.high)}, settings.shadow,
            n -> { settings.shadow = n; saveSettings(); });
        Ui.toggle(this, graphics, R.string.vsync, settings.vsync, on -> { settings.vsync = on; saveSettings(); });
        Ui.toggle(this, graphics, R.string.msaa, settings.prefs.getBoolean("msaa", false), on -> { settings.prefs.edit().putBoolean("msaa", on).apply(); saveSettings(); });
        graphics.addView(Ui.text(this, getString(R.string.graphics_hint), 12, Ui.MUTED));
        LinearLayout driver = Ui.card(this, content, R.string.driver);
        driver.addView(Ui.text(this, getString(R.string.driver_hint), 13, Ui.MUTED));
        gpuStatus = Ui.text(this, gpu, 13, Ui.WHITE); driver.addView(gpuStatus);
        Ui.button(this, driver, R.string.probe, this::probe);
        Ui.button(this, driver, R.string.import_driver, () -> Ui.confirm(this, R.string.driver_confirm, () -> pick(PICK_DRIVER)));
        Switch custom = new Switch(this);
        custom.setText(R.string.custom_driver); custom.setMinHeight(Ui.dp(this, 52));
        custom.setChecked(new File(paths.driver, "enabled").isFile());
        custom.setOnClickListener(view -> {
            boolean on = custom.isChecked();
            try (AppPaths.Lock ignored = paths.lock()) {
                File enabled = new File(paths.driver, "enabled");
                if (on) {
                    if (!new File(paths.driver, "driver.so").isFile()) throw new IOException(getString(R.string.no_driver));
                    SafeFiles.atomicText(enabled, "Imported driver selected by user\n");
                } else if (enabled.exists() && !enabled.delete()) throw new IOException("Cannot disable driver");
            } catch (IOException e) { custom.setChecked(!on); Ui.error(this, e); }
        });
        driver.addView(custom);
        LinearLayout controls = Ui.card(this, content, R.string.controls);
        prefToggle(controls, R.string.touch, "touch", true); prefToggle(controls, R.string.touch_auto, "touch_auto", true);
        prefToggle(controls, R.string.swipe_camera, "swipe_camera", false);
        slider(controls, R.string.touch_size, "touch_size", 70, 150, 100);
        slider(controls, R.string.touch_opacity, "touch_opacity", 20, 90, 65);
        Ui.button(this, controls, R.string.edit_layout, () -> startActivity(new Intent(this, LayoutActivity.class)));
        controls.addView(Ui.text(this, getString(R.string.controller_hint), 13, Ui.MUTED));
        LinearLayout data = Ui.card(this, content, R.string.data);
        prefToggle(data, R.string.show_fps, "show_fps", false);
        Ui.button(this, data, R.string.open_files, this::browseFiles);
        Ui.button(this, data, R.string.backup_saves, () -> pick(EXPORT_SAVES));
        Ui.button(this, data, R.string.restore_saves, () -> Ui.confirm(this, R.string.restore_confirm, () -> pick(RESTORE_SAVES)));
        Ui.button(this, data, R.string.share_logs, () -> async(() -> Reports.create(getApplicationContext(), gpu), uri -> Reports.share(this, uri)));
        Ui.button(this, data, R.string.clear_cache, () -> Ui.confirm(this, R.string.clear_confirm, () -> InstallService.begin(this, InstallService.CLEAR, null)));
        sessionStatus = Ui.text(this, "", 13, Ui.MUTED); data.addView(sessionStatus);
        data.addView(Ui.text(this, getString(R.string.storage_hint), 12, Ui.MUTED));
        LinearLayout about = Ui.card(this, content, R.string.updates);
        about.addView(Ui.text(this, getString(R.string.version, BuildConfig.VERSION_NAME), 14, Ui.WHITE));
        Ui.button(this, about, R.string.check_updates, this::checkUpdates);
        Ui.button(this, about, R.string.licenses, () -> async(() -> {
            try (InputStream in = getAssets().open("licenses/NOTICE.txt")) {
                return new String(SafeFiles.readLimited(in, 512 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            }
        }, text -> new AlertDialog.Builder(this).setTitle(R.string.licenses).setMessage(text).setPositiveButton(R.string.close, null).show()));
        refresh();
    }
    private void prefToggle(LinearLayout box, int label, String key, boolean def) {
        Ui.toggle(this, box, label, settings.prefs.getBoolean(key, def), on -> settings.prefs.edit().putBoolean(key, on).apply());
    }
    private void slider(LinearLayout box, int label, String key, int min, int max, int def) {
        TextView title = Ui.text(this, getString(label) + ": " + settings.prefs.getInt(key, def) + "%", 14, Ui.MUTED); box.addView(title);
        SeekBar bar = new SeekBar(this); bar.setMax(max - min); bar.setProgress(settings.prefs.getInt(key, def) - min); box.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int n, boolean user) {
                if (user) { settings.prefs.edit().putInt(key, n + min).apply(); title.setText(getString(label) + ": " + (n + min) + "%"); }
            }
            public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
        });
    }
    private void saveSettings() {
        try (AppPaths.Lock ignored = paths.lock()) { settings.save(); } catch (IOException e) { Ui.error(this, e); }
    }
    @Override protected void onStart() { super.onStart(); if (content != null) InstallService.observe(this); }
    @Override protected void onStop() { InstallService.unobserve(this); super.onStop(); }
    @Override protected void onResume() { super.onResume(); if (content != null) refresh(); }
    private void refresh() {
        if (gameStatus == null) return;
        boolean valid = false;
        try { GameFiles.validate(paths.game); valid = true; gameStatus.setText(R.string.ready); }
        catch (IOException e) { gameStatus.setText(paths.game.exists() ? e.getMessage() : getString(R.string.not_installed)); }
        play.setEnabled(libraries && !InstallService.busy() && (BuildConfig.DIAGNOSTIC || valid));
        File session = new File(paths.files, "last-session.txt");
        if (session.isFile()) try {
            String previous = SafeFiles.readText(session, 8192).trim();
            sessionStatus.setText(getString(R.string.last_session, previous));
            if (previous.equals("driver-failed")) try (AppPaths.Lock ignored = paths.lock()) {
                File enabled = new File(paths.driver, "enabled");
                if (enabled.exists() && enabled.delete()) sessionStatus.setText(R.string.driver_recovered);
            }
        } catch (IOException e) { sessionStatus.setText(e.getMessage()); }
    }
    @Override public void changed(boolean active, long bytes, String result) {
        if (isFinishing() || content == null) return;
        progress.setVisibility(active ? View.VISIBLE : View.GONE); cancel.setVisibility(active ? View.VISIBLE : View.GONE);
        transferStatus.setText(active ? getString(R.string.working) + "\n" + (bytes / (1024 * 1024)) + " MB" : result);
        if (active) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refresh();
    }
    private void pick(int code) {
        if (InstallService.busy()) { Ui.error(this, new IOException(getString(R.string.busy))); return; }
        Intent intent;
        if (code == PICK_FOLDER) intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        else if (code == EXPORT_SAVES) intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
            .putExtra(Intent.EXTRA_TITLE, "CaseZero-saves.zip").addCategory(Intent.CATEGORY_OPENABLE);
        else intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (code == EXPORT_SAVES) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { startActivityForResult(intent, code); } catch (ActivityNotFoundException e) { Ui.error(this, e); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (SecurityException e) { android.util.Log.w("CaseZero", "Transient document grant", e); }
        }
        String action;
        switch (request) {
            case PICK_PACKAGE: action = InstallService.PACKAGE; break;
            case PICK_ZIP: action = InstallService.ZIP; break;
            case PICK_FOLDER: action = InstallService.FOLDER; break;
            case PICK_DRIVER: action = InstallService.DRIVER; break;
            case EXPORT_SAVES: action = InstallService.BACKUP; break;
            case RESTORE_SAVES: action = InstallService.RESTORE; break;
            default: return;
        }
        InstallService.begin(this, action, uri);
    }
    private void launch() {
        try (AppPaths.Lock ignored = paths.lock()) {
            if (!BuildConfig.DIAGNOSTIC) { GameFiles.validate(paths.game); settings.save(); }
        } catch (IOException e) { Ui.error(this, e); return; }
        startActivity(new Intent(this, GameActivity.class).putExtra("smoke", BuildConfig.DIAGNOSTIC));
    }
    private void browseFiles() {
        Uri root = DocumentsContract.buildRootUri(getPackageName() + ".documents", paths.game.exists() ? "game" : "saves");
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM);
        try { startActivity(intent); } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").putExtra(DocumentsContract.EXTRA_INITIAL_URI, root)); }
            catch (ActivityNotFoundException second) { Ui.error(this, second); }
        }
    }
    private void probe() { gpuStatus.setText(R.string.checking); async(NativeSupport::probe, result -> { gpu = result; gpuStatus.setText(result); }); }
    private void checkUpdates() {
        if (BuildConfig.DIAGNOSTIC) { Toast.makeText(this, R.string.update_unsigned, Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, R.string.checking, Toast.LENGTH_SHORT).show();
        async(Updates::check, release -> {
            if (release == null) { Toast.makeText(this, R.string.no_updates, Toast.LENGTH_LONG).show(); return; }
            new AlertDialog.Builder(this).setTitle(release.name).setMessage(R.string.download_update)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.continue_action, (d, w) ->
                    async(() -> Updates.download(getApplicationContext(), release), uri -> {
                        try { Updates.install(this, uri); } catch (IOException | ActivityNotFoundException e) { Ui.error(this, e); }
                    })).show();
        });
    }
    private <T> void async(Callable<T> work, java.util.function.Consumer<T> done) {
        worker.execute(() -> {
            try { T value = work.call(); runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) done.accept(value); }); }
            catch (Exception | LinkageError e) { runOnUiThread(() -> { if (!isDestroyed() && !isFinishing()) Ui.error(this, e); }); }
        });
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
