package model;

// The STORED form of a password: never the password itself.
// It holds the four values the database keeps in the students table:
//   salt       - random text added before hashing, so equal passwords give different hashes
//   hash       - the result of the hashing method (Base64 text)
//   algorithm  - the method used, e.g. PBKDF2WithHmacSHA256
//   iterations - how many times the method repeats (more = slower for attackers)
// Keeping the algorithm and iterations means we can change the method in the future
// and still check passwords that were saved with the old settings.
//
// OOP idea for your viva: this is an IMMUTABLE value object. All fields are final and
// there are no setters, so once it is created nobody can change it.
public final class PasswordHash {

    private final String salt;
    private final String hash;
    private final String algorithm;
    private final int iterations;

    public PasswordHash(String salt, String hash, String algorithm, int iterations) {
        this.salt = salt;
        this.hash = hash;
        this.algorithm = algorithm;
        this.iterations = iterations;
    }

    public String getSalt()      { return salt; }
    public String getHash()      { return hash; }
    public String getAlgorithm() { return algorithm; }
    public int getIterations()   { return iterations; }

    // Never print the salt or hash by accident (for example in a log message)
    @Override
    public String toString() {
        return "PasswordHash[" + algorithm + ", " + iterations + " iterations, value hidden]";
    }
}
