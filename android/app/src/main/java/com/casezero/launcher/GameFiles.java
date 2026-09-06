package com.casezero.launcher;

import java.io.*;
import java.util.*;

final class GameFiles {
    static final int TITLE_ID = 0x58410A8D;
    static void validate(File root) throws IOException {
        for (String name : new String[]{"default.xex", "data/shaders/deadrisingprologue-ps.big", "data/frontend/fecmn.big"}) {
            File f = new File(root, name);
            if (!f.isFile() || f.length() == 0) throw new IOException("Missing or empty: " + name);
        }
        validateXex(new File(root, "default.xex"));
    }
    static void validateXex(File file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            if (f.length() < 24 || f.readInt() != 0x58455832) throw new IOException("Not an Xbox 360 XEX2 executable");
            f.seek(20); int headers = f.readInt();
            if (headers < 1 || headers > 256 || 24L + headers * 8L > f.length()) throw new IOException("Invalid XEX header table");
            for (int i = 0; i < headers; ++i) {
                f.seek(24L + i * 8L);
                int key = f.readInt(); long offset = Integer.toUnsignedLong(f.readInt());
                if (key == 0x00040006) {
                    if (offset > f.length() - 24) throw new IOException("Truncated XEX execution information");
                    f.seek(offset + 12);
                    if (f.readInt() != TITLE_ID) throw new IOException("Wrong game: expected Case Zero title 58410A8D");
                    return;
                }
            }
            throw new IOException("XEX has no execution/title information");
        }
    }
    static void validatePackage(File file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            if (f.length() < 0x1000) throw new IOException("Truncated Xbox 360 package");
            int magic = f.readInt();
            if (magic != 0x4C495645 && magic != 0x434F4E20 && magic != 0x50495253)
                throw new IOException("Choose a LIVE/CON/PIRS XBLA package, not an ISO or PC file");
            f.seek(0x360);
            if (f.readInt() != TITLE_ID) throw new IOException("Wrong package: expected title 58410A8D");
        }
    }
    static File findRoot(File staging) throws IOException {
        List<File> roots = new ArrayList<>(); scan(staging, 0, roots);
        if (roots.size() != 1) throw new IOException("Expected one game folder with default.xex; found " + roots.size());
        validate(roots.get(0)); return roots.get(0);
    }
    private static void scan(File dir, int depth, List<File> roots) throws IOException {
        if (depth > 32) throw new IOException("Game folder is nested too deeply");
        if (new File(dir, "default.xex").isFile()) { roots.add(dir); return; }
        File[] entries = dir.listFiles();
        if (entries == null) throw new IOException("Cannot read imported folder");
        for (File f : entries) if (f.isDirectory()) scan(f, depth + 1, roots);
    }
    private GameFiles() {}
}
