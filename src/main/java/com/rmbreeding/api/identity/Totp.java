package com.rmbreeding.api.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

final class Totp {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int STEP_SECONDS = 30;

    private Totp() {
    }

    static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    static String code(String secret, Instant at) {
        long counter = at.getEpochSecond() / STEP_SECONDS;
        return hotp(secret, counter);
    }

    static boolean verify(String secret, String code, Instant at) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long counter = at.getEpochSecond() / STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (constantTimeEquals(code, hotp(secret, counter + offset))) {
                return true;
            }
        }
        return false;
    }

    private static String hotp(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute TOTP", ex);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(), right.getBytes());
    }

    private static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    private static byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").toUpperCase();
        int buffer = 0;
        int bits = 0;
        byte[] out = new byte[normalized.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int val = ALPHABET.indexOf(normalized.charAt(i));
            if (val < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }
}
