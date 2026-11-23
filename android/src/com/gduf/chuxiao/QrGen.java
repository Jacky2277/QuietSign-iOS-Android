package com.gduf.chuxiao;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** 与网页端 CryptoJS 一致的 AES-256-ECB + PKCS7，输出 base64（无换行）。 */
class QrGen {

    static String aesEcbB64(String plain, String key) {
        try {
            byte[] kb = key.getBytes("UTF-8");
            if (kb.length != 32 || plain == null || plain.isEmpty()) return null;
            Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kb, "AES"));
            byte[] out = c.doFinal(plain.getBytes("UTF-8"));
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }
}
