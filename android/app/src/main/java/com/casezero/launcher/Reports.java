package com.casezero.launcher;

import android.content.*;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

final class Reports {
    static android.net.Uri create(Context context, String gpu) throws IOException {
        AppPaths p = new AppPaths(context);
        File dir = new File(context.getCacheDir(), "reports"); SafeFiles.mkdir(dir);
        File tmp = new File(dir, "report.tmp"), report = new File(dir, "report.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tmp))) {
            add(zip, "device.txt", ("Case Zero " + BuildConfig.VERSION_NAME + "\n" + Build.MANUFACTURER + " " + Build.MODEL +
                "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                String.join(", ", Build.SUPPORTED_ABIS) + "\n" + gpu + "\n").getBytes(StandardCharsets.UTF_8));
            try (InputStream info = context.getAssets().open("build-info.json")) { add(zip, "build-info.json", SafeFiles.readLimited(info, 65536)); }
            for (File f : new File[]{new File(p.logs, "runtime.log"), new File(p.logs, "previous.log"),
                new File(p.files, "last-session.txt"), new File(p.saves, "cz_settings.txt")}) {
                if (!f.isFile()) continue;
                // Logs can be enormous after a renderer diagnostic. Export a bounded
                // tail; never include game files, save contents or arbitrary app paths.
                try (RandomAccessFile file = new RandomAccessFile(f, "r")) {
                    int n = (int)Math.min(file.length(), 2 * 1024 * 1024);
                    byte[] data = new byte[n]; file.seek(file.length() - n); file.readFully(data); add(zip, f.getName(), data);
                }
            }
        }
        Files.move(tmp.toPath(), report.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return ShareProvider.uri(context, "reports/report.zip");
    }
    static void share(Context c, android.net.Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .setClipData(ClipData.newRawUri("Case Zero diagnostics", uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(i, c.getString(R.string.share_logs)));
    }
    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
    private Reports() {}
}
