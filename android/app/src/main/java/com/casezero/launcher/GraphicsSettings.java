package com.casezero.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.util.Properties;

final class GraphicsSettings {
    private final AppPaths paths;
    final SharedPreferences prefs;
    int width = 1280, height = 720, fps = 30, shadow = 0, fov = 0, mouse = 5;
    boolean vsync = true;
    GraphicsSettings(Context context) throws IOException {
        paths = new AppPaths(context);
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        File cfg = new File(paths.saves, "cz_settings.txt");
        Properties p = new Properties();
        if (cfg.isFile()) try (InputStream in = new FileInputStream(cfg)) { p.load(in); }
        width = number(p, "res_w", 1280, 1280, 6880); height = number(p, "res_h", 720, 720, 2880);
        fps = number(p, "fps_cap", 30, 0, 480);
        if (fps != 30 && fps != 60 && fps != 90 && fps != 120 && fps != 240 && fps != 480 && fps != 0) fps = 30;
        shadow = number(p, "shadow_tier", 0, 0, 2); vsync = number(p, "vsync", 1, 0, 1) == 1;
        fov = number(p, "fov", 0, -10, 30); mouse = number(p, "mouse_sens", 5, 1, 10);
    }
    private static int number(Properties p, String key, int def, int min, int max) {
        try { int n = Integer.parseInt(p.getProperty(key, "")); return n >= min && n <= max ? n : def; }
        catch (NumberFormatException e) { return def; }
    }
    void save() throws IOException {
        SafeFiles.atomicText(new File(paths.saves, "cz_settings.txt"),
            "# Case Zero Android — shared with the in-game settings panel\n" +
            "display_mode=1\nres_w=" + width + "\nres_h=" + height + "\nvsync=" + (vsync ? 1 : 0) +
            "\nshadow_tier=" + shadow + "\nfps_cap=" + fps + "\nfov=" + fov + "\nmouse_sens=" + mouse + "\nrt_shadows=0\n");
        SafeFiles.atomicText(new File(paths.files, "android.env"), "CZ_VK_MSAA=" + (prefs.getBoolean("msaa", false) ? "2" : "1") + "\n");
    }
}
