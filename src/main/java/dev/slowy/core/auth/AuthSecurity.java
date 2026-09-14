package dev.slowy.core.auth;

import org.jspecify.annotations.NullMarked;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

@NullMarked
public final class AuthSecurity {

    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private AuthSecurity() {}

    /**
     * Menghasilkan hash password salted PBKDF2.
     */
    public static String hashPassword(String plaintext) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(plaintext.toCharArray(), salt, ITERATIONS, KEY_LENGTH);

        return "$PBKDF2$" + ITERATIONS + "$" + HEX.formatHex(salt) + "$" + HEX.formatHex(hash);
    }

    public static CompletableFuture<String> hashPasswordAsync(String plaintext) {
        return CompletableFuture.supplyAsync(() -> hashPassword(plaintext));
    }

    /**
     * Memverifikasi password secara aman (constant-time).
     */
    public static boolean checkPassword(String plaintext, String storedHash) {
        if (plaintext.isEmpty() || !storedHash.startsWith("$PBKDF2$")) {
            return false;
        }

        String[] parts = storedHash.split("\\$");
        // Format: ["", "PBKDF2", iterations, salt, hash]
        if (parts.length < 5) return false;

        try {
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = HEX.parseHex(parts[3]);
            byte[] expectedHash = HEX.parseHex(parts[4]);

            byte[] testHash = pbkdf2(plaintext.toCharArray(), salt, iterations, expectedHash.length * Byte.SIZE);
            return MessageDigest.isEqual(testHash, expectedHash);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static CompletableFuture<Boolean> checkPasswordAsync(String plaintext, String storedHash) {
        return CompletableFuture.supplyAsync(() -> checkPassword(plaintext, storedHash));
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to calculate PBKDF2 hash", e);
        }
    }
}
