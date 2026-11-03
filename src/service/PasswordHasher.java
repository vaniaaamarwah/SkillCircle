package service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import model.PasswordHash;

// Turns a typed password into a safe stored form, and checks a typed password
// against a stored one. The password itself is NEVER stored or logged.
//
// HOW IT WORKS (for your viva)
//  1. SALT: 16 random bytes made by SecureRandom. Every password gets its own salt,
//     so two students with the same password still get different hashes.
//  2. HASH: PBKDF2 with HMAC-SHA256 (built into Java) mixes password + salt and repeats
//     65,536 times. Repeating makes guessing passwords slow on purpose.
//  3. CHECK: to verify, hash the typed password with the STORED salt and the STORED
//     iteration count, then compare with the stored hash. We never "decrypt" anything:
//     a hash only goes one way.
//  4. The comparison uses MessageDigest.isEqual, which takes the same time whether the
//     first byte or the last byte differs, so timing cannot leak information.
//
// This is a good learning-project design, not a complete security system
// (for example, there is no lock-out after many wrong attempts).
public final class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 65536;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }   // static helper class: nobody creates an object

    // Hash a NEW password with a fresh random salt and the default settings
    public static PasswordHash hash(String plainPassword) {
        return hash(plainPassword, DEFAULT_ITERATIONS);
    }

    // Same, but with a chosen iteration count (used to upgrade settings later, and by tests)
    public static PasswordHash hash(String plainPassword, int iterations) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password is required.");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(plainPassword, salt, iterations, KEY_BITS);
        return new PasswordHash(
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derived),
                ALGORITHM,
                iterations);
    }

    // True only if the typed password produces exactly the stored hash.
    // Returns false (never throws) for a null password or unreadable stored data.
    public static boolean verify(String typedPassword, PasswordHash stored) {
        if (typedPassword == null || stored == null) {
            return false;
        }
        if (!ALGORITHM.equals(stored.getAlgorithm()) || stored.getIterations() < 1) {
            return false;   // an algorithm we do not know how to check
        }
        try {
            byte[] salt = Base64.getDecoder().decode(stored.getSalt());
            byte[] expected = Base64.getDecoder().decode(stored.getHash());
            byte[] actual = derive(typedPassword, salt, stored.getIterations(), expected.length * 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException e) {
            return false;   // the stored salt/hash is not valid Base64
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available on this computer.", e);
        } finally {
            spec.clearPassword();   // wipe the password characters from memory
        }
    }
}
