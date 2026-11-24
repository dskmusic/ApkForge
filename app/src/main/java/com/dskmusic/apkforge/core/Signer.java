package com.dskmusic.apkforge.core;

import com.android.apksig.ApkSigner;
import java.io.File;
import java.util.Collections;

/** Firma v1+v2+v3 con apksig. */
public final class Signer {
    public static void sign(File unsigned, File out, Jks.Entry e, String alias) throws ForgeException {
        try {
            ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder(alias == null || alias.length() == 0 ? "forge" : alias, e.key, e.chain).build();
            ApkSigner s = new ApkSigner.Builder(Collections.singletonList(sc))
                    .setInputApk(unsigned).setOutputApk(out).setMinSdkVersion(21)
                    .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build();
            s.sign();
            File idsig = new File(out.getPath() + ".idsig");
            if (idsig.exists()) idsig.delete();
        } catch (Exception ex) {
            throw new ForgeException("Falló la firma: " + ex, ex);
        }
    }
}
