package com.dskmusic.apkforge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Sirve (solo lectura) los APK de filesDir/apks para compartirlos; sin androidx. */
public final class ApkShare extends ContentProvider {
    // la autoridad lleva el applicationId (build local y de GitHub conviven sin chocar)
    static Uri uriFor(android.content.Context c, File f) { return Uri.parse("content://" + c.getPackageName() + ".share/" + Uri.encode(f.getName())); }

    private File file(Uri u) throws FileNotFoundException {
        String n = u.getLastPathSegment();
        File dir = new File(getContext().getFilesDir(), "apks");
        File f = n == null ? null : new File(dir, n);
        if (f == null || !f.isFile() || !f.getName().equals(n)) throw new FileNotFoundException(String.valueOf(u));
        return f;
    }

    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        try {
            File f = file(u);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public String getType(Uri u) { return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
