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

    /**
     * Prevents instantiation; use static helpers only.
     */
    private SecurityUtils() {
    }

    /**
     * Hashes a password using PBKDF2-HMAC-SHA256 with a random salt, returning a prefixed string digest.
     *
     * @param password candidate password chars (caller should wipe after use where appropriate)
     * @return reversible encoding-safe string combining iteration count, salt, and hash fragments
     * @throws IllegalStateException when JVM crypto providers cannot supply PBKDF2 SHA-256
     */
    public static String hashPassword(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH_BITS);

        return HASH_PREFIX
                + "$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verifies a candidate password against a stored hashed value ({@link #hashPassword(char[])}) or,
     * for migration, treats non-prefixed values as plaintext for constant-time equality.
     *
     * @param password    candidate chars from the login form
     * @param storedValue database-stored credential string or hash
     * @return {@code true} only when verification succeeds
     */
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

    /**
     * @param storedValue value read from persistence (may be null)
     * @return {@code true} when stored value is not prefixed with PBKDF2 digest marker (legacy plaintext row)
     */
    public static boolean isLegacyPlaintextPassword(String storedValue) {
        return storedValue != null && !storedValue.startsWith(HASH_PREFIX + "$");
    }

    /**
     * Runs PBKDF2 key derivation suitable for comparing password hash bytes.
     *
     * @param password       secret characters
     * @param salt           random salt bytes
     * @param iterations     PBKDF2 iteration cost
     * @param keyLengthBits  derived key length in bits
     * @return derived key octets (length implied by algorithm output)
     * @throws IllegalStateException when derivation fails catastrophically
     */
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }

    /**
     * Constant-time byte array comparison intended to hinder timing probes on hashed bytes.
     *
     * @param a first operand (may not be null for match)
     * @param b second operand
     * @return {@code true} when arrays are same length and hold identical octets
     */
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

    /**
     * Constant-time string comparison used only along the deprecated plaintext compat path.
     *
     * @param a comparable string segment
     * @param b other segment (must equal length for match per side-channel-harder rule)
     * @return equality result without early exit on mismatched prefixes
     */
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
