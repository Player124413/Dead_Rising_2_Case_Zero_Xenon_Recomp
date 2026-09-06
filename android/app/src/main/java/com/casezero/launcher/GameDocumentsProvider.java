package com.casezero.launcher;

import android.database.*;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.util.*;

/** Narrow SAF access. Read-only even for our own UI; mutations use transactional imports. */
public final class GameDocumentsProvider extends DocumentsProvider {
    private static final String[] ROOT_COLUMNS = {DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_MIME_TYPES};
    private static final String[] DOC_COLUMNS = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED};
    @Override public boolean onCreate() { return true; }
    private File root(String id) throws FileNotFoundException {
        AppPaths p = new AppPaths(Objects.requireNonNull(getContext()));
        switch (id) { case "game": return p.game; case "saves": return p.saves; case "logs": return p.logs;
            default: throw new FileNotFoundException("Unknown root"); }
    }
    private File file(String id) throws FileNotFoundException {
        int colon = id.indexOf(':');
        if (colon < 0) return root(id);
        try { return SafeFiles.resolve(root(id.substring(0, colon)), id.substring(colon + 1)); }
        catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        for (String id : new String[]{"game", "saves", "logs"}) {
            String title = "Case Zero — " + id;
            MatrixCursor.RowBuilder row = result.newRow();
            row.add(DocumentsContract.Root.COLUMN_ROOT_ID, id).add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, id)
                .add(DocumentsContract.Root.COLUMN_TITLE, title).add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
                .add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_launcher).add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        }
        return result;
    }
    private void row(MatrixCursor cursor, String id) throws FileNotFoundException {
        File file = file(id);
        if (!file.exists()) throw new FileNotFoundException("Not installed");
        cursor.newRow().add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, id.contains(":") ? file.getName() : "Case Zero — " + id)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream")
            .add(DocumentsContract.Document.COLUMN_FLAGS, 0).add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }
    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOC_COLUMNS); row(cursor, id); return cursor;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOC_COLUMNS);
        File[] files = file(parent).listFiles();
        if (files == null) return cursor;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File child : files) {
            if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue;
            row(cursor, parent + (parent.contains(":") ? "/" : ":") + child.getName());
        }
        return cursor;
    }
    @Override public boolean isChildDocument(String parent, String child) {
        try { return file(child).getCanonicalPath().startsWith(file(parent).getCanonicalPath() + File.separator); }
        catch (IOException e) { return false; }
    }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only. Use Import/Restore in the launcher.");
        if (signal != null) signal.throwIfCanceled();
        File target = file(id);
        if (!target.isFile()) throw new FileNotFoundException("Not a file");
        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
