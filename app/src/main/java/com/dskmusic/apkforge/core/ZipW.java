package com.dskmusic.apkforge.core;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Escritor ZIP mínimo: fecha fija, entradas «stored» alineadas a 4 bytes (como zipalign). */
public final class ZipW implements Closeable {
    private static final int DOS_DATE = ((2026 - 1980) << 9) | (1 << 5) | 1, DOS_TIME = 0;
    private final OutputStream out;
    private long pos = 0;
    private final List<Object[]> central = new ArrayList<Object[]>();
    private static final Charset U8 = Charset.forName("UTF-8");

    public ZipW(File f) throws IOException { out = new BufferedOutputStream(new FileOutputStream(f), 1 << 16); }

    private void w(byte[] b, int off, int len) throws IOException { out.write(b, off, len); pos += len; }
    private void w16(int v) throws IOException { out.write(v & 0xFF); out.write((v >> 8) & 0xFF); pos += 2; }
    private void w32(long v) throws IOException { w16((int) (v & 0xFFFF)); w16((int) ((v >> 16) & 0xFFFF)); }

    private void header(String name, int method, long crc, long csize, long usize, boolean align) throws IOException {
        byte[] nb = name.getBytes(U8);
        long off = pos;
        int pad = 0;
        if (align) { long o = pos + 30 + nb.length; pad = (int) ((4 - o % 4) % 4); }
        int elen = pad > 0 ? 4 + pad : 0;
        w32(0x04034b50L); w16(20); w16(0x0800); w16(method); w16(DOS_TIME); w16(DOS_DATE);
        w32(crc); w32(csize); w32(usize); w16(nb.length); w16(elen);
        w(nb, 0, nb.length);
        if (elen > 0) { w16(0x35D9); w16(pad); w(new byte[pad], 0, pad); }
        central.add(new Object[]{name, method, crc, csize, usize, off});
    }

    /** Añade bytes en memoria. */
    public void add(String name, byte[] data, boolean store) throws IOException {
        CRC32 c = new CRC32(); c.update(data);
        if (store) {
            header(name, 0, c.getValue(), data.length, data.length, true);
            w(data, 0, data.length);
        } else {
            Deflater d = new Deflater(6, true);
            d.setInput(data); d.finish();
            ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(64, data.length / 2));
            byte[] buf = new byte[32768];
            while (!d.finished()) { int n = d.deflate(buf); bo.write(buf, 0, n); }
            d.end();
            byte[] z = bo.toByteArray();
            header(name, 8, c.getValue(), z.length, data.length, false);
            w(z, 0, z.length);
        }
    }

    /** Añade un archivo. Si store, se copia sin comprimir leyéndolo en streaming. */
    public void addFile(String name, File f, boolean store) throws IOException {
        if (!store) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream((int) Math.min(f.length(), 1 << 24));
            InputStream in = new FileInputStream(f);
            try { byte[] b = new byte[32768]; int n; while ((n = in.read(b)) >= 0) bo.write(b, 0, n); } finally { in.close(); }
            add(name, bo.toByteArray(), false);
            return;
        }
        CRC32 c = new CRC32();
        InputStream in = new FileInputStream(f);
        byte[] b = new byte[65536]; int n;
        try { while ((n = in.read(b)) >= 0) c.update(b, 0, n); } finally { in.close(); }
        long len = f.length();
        header(name, 0, c.getValue(), len, len, true);
        in = new FileInputStream(f);
        try { while ((n = in.read(b)) >= 0) w(b, 0, n); } finally { in.close(); }
    }

    @Override
    public void close() throws IOException {
        long cdOff = pos;
        for (Object[] e : central) {
            byte[] nb = ((String) e[0]).getBytes(U8);
            w32(0x02014b50L); w16((3 << 8) | 20); w16(20); w16(0x0800); w16((Integer) e[1]); w16(DOS_TIME); w16(DOS_DATE);
            w32((Long) e[2]); w32((Long) e[3]); w32((Long) e[4]); w16(nb.length); w16(0); w16(0); w16(0); w16(0);
            w32(0644L << 16); w32((Long) e[5]);
            w(nb, 0, nb.length);
        }
        long cdSize = pos - cdOff;
        w32(0x06054b50L); w16(0); w16(0); w16(central.size()); w16(central.size()); w32(cdSize); w32(cdOff); w16(0);
        out.flush(); out.close();
    }
}
