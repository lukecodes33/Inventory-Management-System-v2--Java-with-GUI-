import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 password hashing with random salt, plus verification and legacy plaintext compatibility.
 */
public final class SecurityUtils {
    private static final String HASH_PREFIX = "PBKDF2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    /** Utility holder for password hashing and verification helpers. */
    private SecurityUtils() {
    }

    /** Hashes a password using PBKDF2-HMAC-SHA256 with random salt. */
    public static String hashPassword(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH_BITS);

        return HASH_PREFIX
                + "$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /** Verifies candidate password against hashed or legacy plaintext value. */
    public static boolean verifyPassword(char[] password, String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return false;
        }

        // Backward compatibility for legacy plaintext values.
        if (!storedValue.startsWith(HASH_PREFIX + "$")) {
            return constantTimeEquals(new String(password), storedValue);
        }

        String[] parts = storedValue.split("\\$");
        if (parts.length != 4) {
            return false;
        }

        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
        byte[] candidateHash = pbkdf2(password, salt, iterations, expectedHash.length * 8);
        return constantTimeEquals(candidateHash, expectedHash);
    }

    /** Returns true when stored password value is in legacy plaintext format. */
    public static boolean isLegacyPlaintextPassword(String storedValue) {
        return storedValue != null && !storedValue.startsWith(HASH_PREFIX + "$");
    }

    /** Derives a key using PBKDF2 for password hashing operations. */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }

    /** Constant-time byte array comparison to reduce timing side channels. */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /** Constant-time string comparison for legacy plaintext verification path. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
