package com.uos.lms.kms;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvelopeEncryptionServiceTest {

    @Test
    void returnsPlaintextWhenKmsDisabled() {
        KmsProperties properties = new KmsProperties();
        properties.setEnabled(false);

        NcpKmsClient kmsClient = mock(NcpKmsClient.class);
        EnvelopeEncryptionService service = new EnvelopeEncryptionService(properties, kmsClient);

        String encrypted = service.encrypt("sample-value");
        String decrypted = service.decrypt(encrypted);

        assertThat(encrypted).isEqualTo("sample-value");
        assertThat(decrypted).isEqualTo("sample-value");
    }

    @Test
    void encryptAndDecryptWithEnvelopeWhenKmsEnabled() {
        KmsProperties properties = new KmsProperties();
        properties.setEnabled(true);

        byte[] dataKey = new byte[32];
        Arrays.fill(dataKey, (byte) 7);

        NcpKmsClient kmsClient = mock(NcpKmsClient.class);
        when(kmsClient.createCustomKey())
                .thenReturn(new NcpKmsClient.KmsDataKey("ncpkms:v1:wrapped-key", dataKey.clone()));
        when(kmsClient.decryptCustomKey(anyString()))
                .thenReturn(dataKey.clone());

        EnvelopeEncryptionService service = new EnvelopeEncryptionService(properties, kmsClient);
        String encrypted = service.encrypt("resident-number-plain");
        String decrypted = service.decrypt(encrypted);

        assertThat(encrypted).startsWith("kmsenv:v1:");
        assertThat(encrypted).isNotEqualTo("resident-number-plain");
        assertThat(decrypted).isEqualTo("resident-number-plain");
        verify(kmsClient).decryptCustomKey("ncpkms:v1:wrapped-key");
    }
}
