package com.dskmusic.apkforge.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;

/** Parches del AndroidManifest binario (AXML) y de resources.arsc. Port de forge.py. */
public final class Axml {
    private static final Charset U8 = Charset.forName("UTF-8"), U16 = Charset.forName("UTF-16LE");

    private static ByteBuffer bb(byte[] d) { return ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN); }
    private static int u8(byte[] d, int p) { return d[p] & 0xFF; }
    private static int u16(byte[] d, int p) { return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8); }
    private static long u32(byte[] d, int p) { return bb(d).getInt(p) & 0xFFFFFFFFL; }

    static final class Pool { List<String> strings; boolean utf8; int size; }

    private static Pool readPool(byte[] d, int off) throws ForgeException {
        int typ = u16(d, off), hsz = u16(d, off + 2);
        int size = (int) u32(d, off + 4), scount = (int) u32(d, off + 8), stcount = (int) u32(d, off + 12);
        long flags = u32(d, off + 16);
        int sstart = (int) u32(d, off + 20);
        if (typ != 1) throw new ForgeException("AXML: pool de strings no encontrado");
        if (stcount != 0) throw new ForgeException("AXML: pool con estilos no soportado");
        boolean utf8 = (flags & 0x100) != 0;
        List<String> out = new ArrayList<String>();
        int base = off + sstart;
        for (int i = 0; i < scount; i++) {
            int p = base + (int) u32(d, off + hsz + 4 * i);
            if (utf8) {
                int l = u8(d, p); p++;
                if ((l & 0x80) != 0) { p++; }
                int bl = u8(d, p); p++;
                if ((bl & 0x80) != 0) { bl = ((bl & 0x7F) << 8) | u8(d, p); p++; }
                out.add(new String(d, p, bl, U8));
            } else {
                int l = u16(d, p); p += 2;
                if ((l & 0x8000) != 0) { l = ((l & 0x7FFF) << 16) | u16(d, p); p += 2; }
                out.add(new String(d, p, l * 2, U16));
            }
        }
        Pool pl = new Pool(); pl.strings = out; pl.utf8 = utf8; pl.size = size;
        return pl;
    }

    private static void ln8(java.io.ByteArrayOutputStream o, int n) {
        if (n < 0x80) o.write(n); else { o.write(0x80 | (n >> 8)); o.write(n & 0xFF); }
    }

    private static byte[] buildPool(List<String> strings, boolean utf8) {
        List<byte[]> blobs = new ArrayList<byte[]>();
        for (String s : strings) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            if (utf8) {
                byte[] b = s.getBytes(U8);
                ln8(o, s.length()); ln8(o, b.length);
                o.write(b, 0, b.length); o.write(0);
            } else {
                int n = s.length();
                if (n < 0x8000) { o.write(n & 0xFF); o.write(n >> 8); }
                else { int h = 0x8000 | (n >> 16); o.write(h & 0xFF); o.write(h >> 8); o.write(n & 0xFF); o.write((n & 0xFFFF) >> 8); }
                byte[] b = s.getBytes(U16);
                o.write(b, 0, b.length); o.write(0); o.write(0);
            }
            blobs.add(o.toByteArray());
        }
        int cur = 0, total = 0;
        int[] offs = new int[blobs.size()];
        for (int i = 0; i < blobs.size(); i++) { offs[i] = cur; cur += blobs.get(i).length; }
        total = cur;
        int padded = total + ((4 - total % 4) % 4);
        int hsz = 28, sstart = hsz + 4 * strings.size(), size = sstart + padded;
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 1).putShort((short) hsz).putInt(size).putInt(strings.size()).putInt(0).putInt(utf8 ? 0x100 : 0).putInt(sstart).putInt(0);
        for (int o : offs) b.putInt(o);
        for (byte[] x : blobs) b.put(x);
        return b.array();
    }

    public static byte[] patchManifest(byte[] data, Map<String, String> repl, Long versionCode) throws ForgeException {
        if (u16(data, 0) != 3) throw new ForgeException("AndroidManifest no es AXML");
        Pool pool = readPool(data, 8);
        List<String> strings = pool.strings;
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < strings.size(); i++) {
            String r = repl.get(strings.get(i));
            if (r != null) { seen.add(strings.get(i)); strings.set(i, r); }
        }
        List<String> missing = new ArrayList<String>(repl.keySet());
        missing.removeAll(seen);
        if (!missing.isEmpty()) { Collections.sort(missing); throw new ForgeException("Plantilla base: faltan marcadores " + missing); }
        byte[] np = buildPool(strings, pool.utf8);
        byte[] rest = Arrays.copyOfRange(data, 8 + pool.size, data.length);
        if (versionCode != null) {
            ByteBuffer r = bb(rest);
            int pos = 0; boolean done = false;
            while (pos + 8 <= rest.length && !done) {
                int ctype = u16(rest, pos); long csize = u32(rest, pos + 4);
                if (csize < 8) break;
                if (ctype == 0x0102) {
                    int ext = pos + 16;
                    int nm = (int) u32(rest, ext + 4), astart = u16(rest, ext + 8), asz = u16(rest, ext + 10), acnt = u16(rest, ext + 12);
                    if (nm < strings.size() && "manifest".equals(strings.get(nm))) {
                        for (int k = 0; k < acnt; k++) {
                            int ao = ext + astart + k * asz;
                            int anm = (int) u32(rest, ao + 4);
                            if (anm < strings.size() && "versionCode".equals(strings.get(anm))) {
                                r.putInt(ao + 16, (int) (versionCode & 0xFFFFFFFFL));
                                r.putInt(ao + 8, -1);
                                rest[ao + 14] = 0; rest[ao + 15] = 0x10;
                                done = true;
                            }
                        }
                        break;
                    }
                }
                pos += (int) csize;
            }
            if (!done) throw new ForgeException("No se encontró versionCode en el manifest base");
        }
        int total = 8 + np.length + rest.length;
        ByteBuffer o = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        o.putShort((short) 3).putShort((short) 8).putInt(total).put(np).put(rest);
        return o.array();
    }

    public static byte[] patchArscPackage(byte[] data, String newpkg) throws ForgeException {
        byte[] d = data.clone();
        if (u16(d, 0) != 2) throw new ForgeException("resources.arsc inválido");
        int pos = u16(d, 2);
        long total = u32(d, 4);
        while (pos + 8 <= Math.min(total, d.length)) {
            int ctype = u16(d, pos); long csize = u32(d, pos + 4);
            if (ctype == 0x0200) {
                byte[] raw = newpkg.getBytes(U16);
                if (raw.length > 254) throw new ForgeException("Nombre de paquete demasiado largo");
                Arrays.fill(d, pos + 12, pos + 12 + 256, (byte) 0);
                System.arraycopy(raw, 0, d, pos + 12, raw.length);
                return d;
            }
            pos += (int) csize;
        }
        throw new ForgeException("resources.arsc: no se encontró la tabla de paquete");
    }
}
