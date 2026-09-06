package com.casezero.launcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.*;

/** SDK-free file operations: containment, bounded streaming, cancellation, atomic replacement. */
final class SafeFiles {
    interface Progress { void update(long bytes) throws IOException; }
    static final class Budget {
        final long maxBytes;
        final int maxFiles;
        long bytes;
        int files;
        final Progress progress;
        Budget(long maxBytes, int maxFiles, Progress progress) {
            this.maxBytes = maxBytes; this.maxFiles = maxFiles; this.progress = progress;
        }
        void file() throws IOException {
            if (++files > maxFiles) throw new IOException("Too many files");
        }
        void add(int n) throws IOException {
            if (n < 0 || bytes > maxBytes - n) throw new IOException("Import exceeds size limit");
            bytes += n;
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
            progress.update(bytes);
        }
    }
    static String relative(String name) throws IOException {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0 ||
            name.indexOf(':') >= 0 || name.indexOf('\0') >= 0) throw new IOException("Unsafe path: " + name);
        String clean = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        String[] parts = clean.split("/", -1);
        if (parts.length > 32) throw new IOException("Path is too deep");
        for (String p : parts)
            if (p.isEmpty() || p.equals(".") || p.equals("..") || p.length() > 255)
                throw new IOException("Unsafe path: " + name);
        return clean;
    }
    static File resolve(File root, String name) throws IOException {
        File canonicalRoot = root.getCanonicalFile();
        File file = new File(canonicalRoot, relative(name)).getCanonicalFile();
        if (!file.getPath().startsWith(canonicalRoot.getPath() + File.separator))
            throw new IOException("Path escapes import directory");
        return file;
    }
    static void mkdir(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
    }
    static void copy(InputStream in, File target, Budget budget) throws IOException {
        budget.file(); mkdir(target.getParentFile());
        if (!target.createNewFile()) throw new IOException("Duplicate file: " + target.getName());
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            long checkAt = budget.bytes;
            for (int n; (n = in.read(buffer)) != -1;) {
                budget.add(n);
                if (budget.bytes >= checkAt) {
                    if (target.getUsableSpace() < 128L * 1024 * 1024)
                        throw new IOException("Not enough free space (128 MB reserve required)");
                    checkAt = budget.bytes + 8L * 1024 * 1024;
                }
                out.write(buffer, 0, n);
            }
            out.getFD().sync();
        }
    }
    static void unzip(InputStream in, File root, Budget budget) throws IOException {
        mkdir(root);
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(in))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
                String name = relative(e.getName());
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate ZIP path: " + name);
                File dst = resolve(root, name);
                if (e.isDirectory()) { budget.file(); budget.add(0); mkdir(dst); }
                else copy(zip, dst, budget);
                zip.closeEntry(); // validates CRC, including unknown-size streaming entries
            }
        }
        if (names.isEmpty()) throw new IOException("Empty or invalid ZIP archive");
    }
    static void deleteTree(File file) throws IOException {
        // Never follow a symlink while clearing caches/staging.
        if (Files.isSymbolicLink(file.toPath())) { Files.delete(file.toPath()); return; }
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read " + file);
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete " + file);
    }
    static void atomicText(File dst, String content) throws IOException {
        mkdir(dst.getParentFile());
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.getFD().sync();
        }
        Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    static void replaceDirectory(File incoming, File current) throws IOException {
        File old = new File(current.getParentFile(), current.getName() + ".previous");
        recoverDirectory(current);
        if (old.exists()) deleteTree(old);
        if (current.exists() && !current.renameTo(old)) throw new IOException("Cannot back up current installation");
        if (!incoming.renameTo(current)) {
            if (old.exists() && !old.renameTo(current)) throw new IOException("Install failed; backup retained at " + old);
            throw new IOException("Cannot activate new installation");
        }
        // Keep the previous tree until the next operation. A cleanup error must
        // never report a successfully committed installation as a failed import.
    }
    static void recoverDirectory(File current) throws IOException {
        File old = new File(current.getParentFile(), current.getName() + ".previous");
        if (!current.exists() && old.exists() && !old.renameTo(current))
            throw new IOException("Cannot recover previous installation: " + old);
    }
    static void zipDirectory(File root, OutputStream stream, Progress progress) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(stream))) {
            zipWalk(root, root, zip, progress, new long[]{0});
        }
    }
    private static void zipWalk(File root, File dir, ZipOutputStream zip, Progress p, long[] done) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("Cannot read " + dir);
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) throw new IOException("Symlinks are not exportable");
            if (child.isDirectory()) zipWalk(root, child, zip, p, done);
            else {
                String name = root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/');
                // Graphics settings are user/device state, not save data.
                if (name.equals("cz_settings.txt")) continue;
                zip.putNextEntry(new ZipEntry(relative(name)));
                try (InputStream in = new FileInputStream(child)) {
                    byte[] buf = new byte[64 * 1024];
                    for (int n; (n = in.read(buf)) != -1;) { zip.write(buf, 0, n); done[0] += n; p.update(done[0]); }
                }
                zip.closeEntry();
            }
        }
    }
    static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1;) {
            if (out.size() > limit - n) throw new IOException("Input exceeds size limit");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    static String readText(File file, int limit) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return new String(readLimited(in, limit), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private SafeFiles() {}
}
