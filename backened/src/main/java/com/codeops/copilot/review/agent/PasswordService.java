package com.codeops.copilot.review.agent;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordService {
    private static final SecureRandom RANDOM = new SecureRandom();
    static final int MIN_PASSWORD_LENGTH = 6;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private PasswordService() {}

    static String hash(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS, KEY_BITS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
                + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(derived);
    }

    static boolean matches(String password, String encoded) {
        try {
            if (password == null || encoded == null || !encoded.startsWith("pbkdf2$")) return false;
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return java.security.MessageDigest.isEqual(expected, derive(password, salt, iterations, expected.length * 8));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int bits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        }
    }
}
