package com.dskmusic.apkforge;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.dskmusic.apkforge.core.ForgeException;

import java.io.*;

/** Copia una carpeta elegida con el selector del sistema (SAF) a un directorio local, con todas sus subcarpetas. */
final class FolderCopy {
    private final ContentResolver cr;
    private final long maxBytes; private final int maxFiles;
    private long total = 0; private int files = 0;

    FolderCopy(ContentResolver cr, long maxBytes, int maxFiles) { this.cr = cr; this.maxBytes = maxBytes; this.maxFiles = maxFiles; }

    void copy(Uri tree, File dest) throws Exception {
        String docId = DocumentsContract.getTreeDocumentId(tree);
        walk(tree, docId, dest);
    }

    private void walk(Uri tree, String docId, File dest) throws Exception {
        dest.mkdirs();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = cr.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) return;
        java.util.List<String[]> items = new java.util.ArrayList<String[]>();
        try { while (c.moveToNext()) items.add(new String[]{c.getString(0), c.getString(1), c.getString(2)}); } finally { c.close(); }
        for (String[] it : items) {
            String name = it[1] == null ? "" : it[1].replace("/", "_");
            if (name.length() == 0 || name.equals(".") || name.equals("..")) continue;
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(it[2])) { walk(tree, it[0], new File(dest, name)); continue; }
            if (name.equals(".DS_Store") || name.equals("Thumbs.db") || name.startsWith("._")) continue;
            if (++files > maxFiles) throw new ForgeException("La carpeta tiene demasiados archivos");
            InputStream in = cr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, it[0]));
            if (in == null) continue;
            OutputStream out = new FileOutputStream(new File(dest, name));
            try {
                byte[] b = new byte[65536]; int n;
                while ((n = in.read(b)) >= 0) { total += n; if (total > maxBytes) throw new ForgeException("La carpeta supera el límite de " + (maxBytes >> 20) + " MB"); out.write(b, 0, n); }
            } finally { in.close(); out.close(); }
        }
    }
}
