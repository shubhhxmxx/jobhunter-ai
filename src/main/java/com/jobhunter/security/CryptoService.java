package com.jobhunter.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** AES-256-GCM encryption for provider keys at rest. The master key comes from the environment, never from the repo. */
@Component
public class CryptoService {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${jobhunter.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("JOBHUNTER_ENCRYPTION_KEY is not set. Generate one with: openssl rand -base64 32");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 32) throw new IllegalStateException("JOBHUNTER_ENCRYPTION_KEY must decode to exactly 32 bytes");
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed");
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, 12));
            return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed");
        }
    }

    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final String DUMMY_HASH = hashPasscode("not-a-real-passcode");

    /** Salted PBKDF2 hash in the form pbkdf2$iterations$salt$hash. */
    public static String hashPasscode(String passcode) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(pbkdf2(passcode, salt, PBKDF2_ITERATIONS));
        } catch (Exception e) {
            throw new IllegalStateException("Hashing failed");
        }
    }

    /** Constant-time check. A missing hash is compared against a dummy so unknown accounts cost the same time as known ones. */
    public static boolean verifyPasscode(String passcode, String stored) {
        try {
            boolean known = stored != null && stored.startsWith("pbkdf2$");
            String[] parts = (known ? stored : DUMMY_HASH).split("\\$");
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(passcode, Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1]));
            return MessageDigest.isEqual(expected, actual) && known;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String passcode, byte[] salt, int iterations) throws Exception {
        var spec = new javax.crypto.spec.PBEKeySpec(passcode.toCharArray(), salt, iterations, 256);
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    public static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
