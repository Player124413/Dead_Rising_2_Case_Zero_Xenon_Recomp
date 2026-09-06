package com.casezero.launcher;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.Objects;

/** Only two exact app-created files can be granted to the share sheet/installer. */
public final class ShareProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (!"/reports/report.zip".equals(path) && !"/updates/update.apk".equals(path))
            throw new FileNotFoundException("Not an exportable file");
        return new File(Objects.requireNonNull(getContext()).getCacheDir(), path.substring(1));
    }
    static Uri uri(Context c, String path) { return Uri.parse("content://" + c.getPackageName() + ".share/" + path); }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) {
        return "/updates/update.apk".equals(uri.getPath()) ? "application/vnd.android.package-archive" : "application/zip";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        try {
            File f = file(uri);
            String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor c = new MatrixCursor(cols); MatrixCursor.RowBuilder row = c.newRow();
            for (String col : cols) if (OpenableColumns.DISPLAY_NAME.equals(col)) row.add(col, f.getName());
                else if (OpenableColumns.SIZE.equals(col)) row.add(col, f.length());
            return c;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
}
