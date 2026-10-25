package com.androidnas;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;

public class TotpManager {
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static String generateSecret() {
        byte[] buffer = new byte[20];
        new java.security.SecureRandom().nextBytes(buffer);
        return base32Encode(buffer);
    }

    public static boolean verifyCode(String secret, int code) {
        try {
            long time = System.currentTimeMillis() / 1000 / 30;
            for (int i = -1; i <= 1; i++) {
                if (calcCode(secret, time + i) == code) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int calcCode(String secret, long time) throws Exception {
        byte[] key = base32Decode(secret);
        byte[] data = new byte[8];
        long value = time;
        for (int i = 8; i-- > 0; value >>>= 8) { data[i] = (byte) value; }
        SecretKeySpec signKey = new SecretKeySpec(key, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0xF;
        int truncatedHash = 0;
        for (int i = 0; i < 4; ++i) {
            truncatedHash <<= 8;
            truncatedHash |= (hash[offset + i] & 0xFF);
        }
        truncatedHash &= 0x7FFFFFFF;
        truncatedHash %= 1000000;
        return truncatedHash;
    }

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF); bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 0x1F)); bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        return sb.toString();
    }

    private static byte[] base32Decode(String base32) {
        base32 = base32.trim().replace(" ", "").toUpperCase();
        int buffer = 0, bitsLeft = 0, count = 0;
        for (char c : base32.toCharArray()) {
            if (c == '=') continue;
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val; bitsLeft += 5;
            if (bitsLeft >= 8) { count++; bitsLeft -= 8; }
        }
        byte[] result = new byte[count];
        buffer = 0; bitsLeft = 0; int index = 0;
        for (char c : base32.toCharArray()) {
            if (c == '=') continue;
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val; bitsLeft += 5;
            if (bitsLeft >= 8) { result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF); bitsLeft -= 8; }
        }
        return result;
    }
}
