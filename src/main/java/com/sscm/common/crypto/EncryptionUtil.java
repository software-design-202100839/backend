package com.sscm.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * SHA-256 해시 유틸리티.
 * 토큰 해시 저장 등에 사용.
 */
public final class EncryptionUtil {

    private EncryptionUtil() {}

    public static String sha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 hashing failed", e);
        }
    }
}
