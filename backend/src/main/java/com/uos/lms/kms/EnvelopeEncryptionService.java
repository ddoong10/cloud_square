package com.uos.lms.kms;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class EnvelopeEncryptionService {

    private static final String ENVELOPE_PREFIX = "kmsenv:v1";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final KmsProperties kmsProperties;
    private final NcpKmsClient ncpKmsClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (!kmsProperties.isEnabled()) {
            return plaintext;
        }

        NcpKmsClient.KmsDataKey dataKey = ncpKmsClient.createCustomKey();
        byte[] keyBytes = dataKey.plainKey();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return ENVELOPE_PREFIX
                    + ":" + b64UrlEncode(dataKey.ciphertext().getBytes(StandardCharsets.UTF_8))
                    + ":" + b64UrlEncode(iv)
                    + ":" + b64UrlEncode(cipherBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Envelope encryption failed", exception);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        if (!isEnvelopeValue(storedValue)) {
            return storedValue;
        }
        if (!kmsProperties.isEnabled()) {
            throw new IllegalStateException("Encrypted data exists but KMS is disabled");
        }

        String[] parts = storedValue.split(":");
        if (parts.length != 5) {
            throw new IllegalStateException("Invalid envelope format");
        }

        String wrappedKey = new String(b64UrlDecode(parts[2]), StandardCharsets.UTF_8);
        byte[] iv = b64UrlDecode(parts[3]);
        byte[] cipherText = b64UrlDecode(parts[4]);

        byte[] keyBytes = ncpKmsClient.decryptCustomKey(wrappedKey);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Envelope decryption failed", exception);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    public boolean isEnvelopeValue(String value) {
        return value != null && value.startsWith(ENVELOPE_PREFIX + ":");
    }

    private String b64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] b64UrlDecode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }
}
