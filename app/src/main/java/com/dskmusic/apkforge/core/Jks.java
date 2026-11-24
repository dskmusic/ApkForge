package com.dskmusic.apkforge.core;

import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/** Lector de keystores JKS (Android no trae el tipo "JKS") y PKCS12. */
public final class Jks {
    public static final class Entry {
        public PrivateKey key;
        public List<X509Certificate> chain = new ArrayList<X509Certificate>();
    }

    public static List<String> aliases(byte[] ksBytes, char[] storePass) throws Exception {
        if (isJks(ksBytes)) return new ArrayList<String>(parse(ksBytes, storePass, null, null).keySet());
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(ksBytes), storePass);
        return Collections.list(ks.aliases());
    }

    public static boolean isJks(byte[] b) {
        return b.length > 4 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xED && (b[2] & 0xFF) == 0xFE && (b[3] & 0xFF) == 0xED;
    }

    /** Carga la clave privada y la cadena de certificados de un alias (alias null o vacío = el único/primero). */
    public static Entry load(byte[] ksBytes, char[] storePass, String alias, char[] keyPass) throws Exception {
        if (keyPass == null || keyPass.length == 0) keyPass = storePass;
        if (isJks(ksBytes)) {
            Map<String, Entry> m = parse(ksBytes, storePass, alias, keyPass);
            if (m.isEmpty()) throw new ForgeException("El keystore no tiene claves privadas");
            Entry e = alias == null || alias.length() == 0 ? m.values().iterator().next() : m.get(alias.toLowerCase(Locale.ROOT));
            if (e == null) throw new ForgeException("No existe el alias «" + alias + "». Disponibles: " + m.keySet());
            return e;
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(ksBytes), storePass);
        String a = alias;
        if (a == null || a.length() == 0 || !ks.containsAlias(a)) {
            if (a != null && a.length() > 0) throw new ForgeException("No existe el alias «" + a + "». Disponibles: " + Collections.list(ks.aliases()));
            a = Collections.list(ks.aliases()).get(0);
        }
        Key k = ks.getKey(a, keyPass);
        if (!(k instanceof PrivateKey)) throw new ForgeException("El alias no contiene una clave privada");
        Entry e = new Entry();
        e.key = (PrivateKey) k;
        for (java.security.cert.Certificate c : ks.getCertificateChain(a)) e.chain.add((X509Certificate) c);
        return e;
    }

    private static byte[] pwBytes(char[] pw) {
        byte[] b = new byte[pw.length * 2];
        for (int i = 0; i < pw.length; i++) { b[2 * i] = (byte) (pw[i] >> 8); b[2 * i + 1] = (byte) pw[i]; }
        return b;
    }

    private static Map<String, Entry> parse(byte[] data, char[] storePass, String wantAlias, char[] keyPass) throws Exception {
        if (data.length < 32) throw new ForgeException("Keystore JKS no válido");
        // integridad (comprueba la contraseña del keystore)
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(pwBytes(storePass));
        md.update("Mighty Aphrodite".getBytes("UTF-8"));
        md.update(data, 0, data.length - 20);
        byte[] dig = md.digest();
        if (!MessageDigest.isEqual(dig, Arrays.copyOfRange(data, data.length - 20, data.length)))
            throw new ForgeException("Contraseña del keystore incorrecta (o archivo dañado)");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, data.length - 20));
        in.readInt();
        int ver = in.readInt(), count = in.readInt();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Map<String, Entry> out = new LinkedHashMap<String, Entry>();
        for (int i = 0; i < count; i++) {
            int tag = in.readInt();
            String alias = in.readUTF();
            in.readLong();
            if (tag == 1) {
                byte[] enc = new byte[in.readInt()];
                in.readFully(enc);
                int n = in.readInt();
                Entry e = new Entry();
                for (int c = 0; c < n; c++) {
                    if (ver == 2) in.readUTF();
                    byte[] cb = new byte[in.readInt()];
                    in.readFully(cb);
                    e.chain.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cb)));
                }
                String key = alias.toLowerCase(Locale.ROOT);
                if (wantAlias == null || wantAlias.length() == 0 || key.equals(wantAlias.toLowerCase(Locale.ROOT))) {
                    e.key = decryptKey(enc, keyPass);
                }
                out.put(key, e);
            } else if (tag == 2) {
                if (ver == 2) in.readUTF();
                byte[] cb = new byte[in.readInt()];
                in.readFully(cb);
            } else throw new ForgeException("Keystore JKS con entradas no reconocidas");
        }
        return out;
    }

    private static PrivateKey decryptKey(byte[] epki, char[] pw) throws Exception {
        // EncryptedPrivateKeyInfo ::= SEQUENCE { SEQUENCE { OID, NULL }, OCTET STRING }
        int[] p = {0};
        der(epki, p, 0x30);
        der(epki, p, 0x30, true);   // algoritmo (se salta)
        byte[] blob = der(epki, p, 0x04);
        if (blob.length < 40) throw new ForgeException("Clave JKS no válida");
        byte[] salt = Arrays.copyOfRange(blob, 0, 20);
        byte[] enc = Arrays.copyOfRange(blob, 20, blob.length - 20);
        byte[] chk = Arrays.copyOfRange(blob, blob.length - 20, blob.length);
        byte[] pwb = pwBytes(pw);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] plain = new byte[enc.length], digest = salt;
        for (int i = 0; i < enc.length; ) {
            md.update(pwb); md.update(digest);
            digest = md.digest();
            for (int j = 0; j < digest.length && i < enc.length; j++, i++) plain[i] = (byte) (enc[i] ^ digest[j]);
        }
        md.update(pwb); md.update(plain);
        if (!MessageDigest.isEqual(md.digest(), chk)) throw new ForgeException("Contraseña de la clave (alias) incorrecta");
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(plain);
        for (String alg : new String[]{"RSA", "EC", "DSA"}) {
            try { return KeyFactory.getInstance(alg).generatePrivate(spec); } catch (Exception e) { }
        }
        throw new ForgeException("Tipo de clave no soportado");
    }

    /** Lee un elemento DER con la etiqueta esperada. skip=true: devuelve null y avanza al final del elemento; si no, entra en los SEQUENCE. */
    private static byte[] der(byte[] d, int[] p, int tag) throws ForgeException { return der(d, p, tag, false); }

    private static byte[] der(byte[] d, int[] p, int tag, boolean skip) throws ForgeException {
        if ((d[p[0]] & 0xFF) != tag) throw new ForgeException("DER inesperado");
        p[0]++;
        int l = d[p[0]++] & 0xFF;
        if ((l & 0x80) != 0) { int n = l & 0x7F; l = 0; for (int i = 0; i < n; i++) l = (l << 8) | (d[p[0]++] & 0xFF); }
        if (tag == 0x30 && !skip) return null;   // entrar en la secuencia
        byte[] v = Arrays.copyOfRange(d, p[0], p[0] + l);
        p[0] += l;
        return v;
    }
}
