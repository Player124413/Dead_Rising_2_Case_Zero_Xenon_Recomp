package com.casezero.launcher;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** User-initiated GitHub updates. Digest AND Android signing identity are mandatory. */
final class Updates {
    static final long MAX_APK = 256L * 1024 * 1024;
    static final class Release {
        String name, url, digest; long size;
    }
    private static HttpsURLConnection connect(String value) throws IOException {
        URL url = new URL(value);
        for (int redirects = 0; redirects <= 5; ++redirects) {
            String host = url.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443) ||
                !Arrays.asList("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com").contains(host))
                throw new IOException("Untrusted update URL");
            HttpsURLConnection c = (HttpsURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "CaseZero-Android/" + BuildConfig.VERSION_NAME);
            int status = c.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = c.getHeaderField("Location"); c.disconnect();
                if (location == null) throw new IOException("Update redirect has no target");
                url = new URL(url, location); continue;
            }
            if (status == 404) { c.disconnect(); throw new FileNotFoundException("No release published"); }
            if (status != 200) { c.disconnect(); throw new IOException("GitHub HTTP " + status); }
            return c;
        }
        throw new IOException("Too many update redirects");
    }
    static Release check() throws Exception {
        if (BuildConfig.DIAGNOSTIC) return null;
        HttpsURLConnection c;
        try { c = connect(BuildConfig.RELEASES_API); } catch (FileNotFoundException e) { return null; }
        JSONObject json;
        try (InputStream in = c.getInputStream()) { json = new JSONObject(new String(SafeFiles.readLimited(in, 1024 * 1024), StandardCharsets.UTF_8)); }
        finally { c.disconnect(); }
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null;
        JSONArray assets = json.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); ++i) {
            JSONObject asset = assets.getJSONObject(i);
            if (!"CaseZeroRecomp-android-arm64-v8a.apk".equals(asset.optString("name"))) continue;
            Release r = new Release(); r.name = json.optString("tag_name");
            r.url = asset.getString("browser_download_url"); r.digest = asset.optString("digest"); r.size = asset.getLong("size");
            if (!r.url.startsWith("https://github.com/Player124413/Dead_Rising_2_Case_Zero_Xenon_Recomp/releases/download/") ||
                !r.digest.matches("sha256:[a-fA-F0-9]{64}") || r.size <= 0 || r.size > MAX_APK)
                throw new IOException("Release APK has no trusted SHA-256/size/URL metadata");
            // The real version check happens on the signed APK, not an arbitrary tag.
            return r;
        }
        return null;
    }
    static Uri download(Context context, Release release) throws Exception {
        File dir = new File(context.getCacheDir(), "updates"); SafeFiles.mkdir(dir);
        File part = new File(dir, "download.part"), ready = new File(dir, "update.apk");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            HttpsURLConnection c = connect(release.url);
            long count = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] bytes = new byte[128 * 1024];
                for (int n; (n = in.read(bytes)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
                    count += n;
                    if (count > MAX_APK || count > release.size) throw new IOException("Update exceeds declared size");
                    out.write(bytes, 0, n); hash.update(bytes, 0, n);
                }
                out.getFD().sync();
            } finally { c.disconnect(); }
            if (count != release.size || !hex(hash.digest()).equalsIgnoreCase(release.digest.substring(7)))
                throw new IOException("Update SHA-256 or size does not match GitHub");
            PackageManager pm = context.getPackageManager();
            PackageInfo incoming = pm.getPackageArchiveInfo(part.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (incoming == null || !context.getPackageName().equals(incoming.packageName)) throw new IOException("Wrong APK package");
            if (incoming.getLongVersionCode() <= installed.getLongVersionCode()) throw new IOException(context.getString(R.string.no_updates));
            if (!certificates(installed).equals(certificates(incoming))) throw new IOException("APK signing certificate differs from the installed app");
            Files.move(part.toPath(), ready.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return ShareProvider.uri(context, "updates/update.apk");
        } finally { if (part.exists() && !part.delete()) android.util.Log.w("CaseZero", "Could not remove partial update"); }
    }
    private static Set<String> certificates(PackageInfo info) throws Exception {
        if (info.signingInfo == null) throw new IOException("APK has no verified signing information");
        Set<String> values = new HashSet<>();
        for (android.content.pm.Signature signature : info.signingInfo.getApkContentsSigners())
            values.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        if (values.isEmpty()) throw new IOException("APK has no signing certificate");
        return values;
    }
    private static String hex(byte[] value) {
        StringBuilder s = new StringBuilder(); for (byte b : value) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString();
    }
    static void install(Context c, Uri uri) throws IOException {
        if (!c.getPackageManager().canRequestPackageInstalls()) {
            c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())));
            throw new IOException(c.getString(R.string.unknown_sources));
        }
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Verified update", uri));
        c.startActivity(intent);
    }
    private Updates() {}
}
