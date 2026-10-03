package com.medislot.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.medislot.common.crypto.AesGcmConverter;
import com.medislot.common.crypto.EncryptionProperties;

class AesGcmConverterTest {

    private static String key(int fill) {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, (byte) fill);
        return Base64.getEncoder().encodeToString(k);
    }

    private final AesGcmConverter converter = new AesGcmConverter(new EncryptionProperties(key(1)));

    @Test
    void roundTripRestoresPlaintextAndHidesIt() {
        String stored = converter.convertToDatabaseColumn("1990-123-4567");

        assertThat(stored).doesNotContain("1990").doesNotContain("4567");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("1990-123-4567");
    }

    @Test
    void nullAndEmptyAreHandled() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(""))).isEmpty();
    }

    @Test
    void samePlaintextGetsDifferentCiphertextEachTime() {
        assertThat(converter.convertToDatabaseColumn("same")).isNotEqualTo(converter.convertToDatabaseColumn("same"));
    }

    @Test
    void tamperedCiphertextFailsLoudly() {
        byte[] raw = Base64.getDecoder().decode(converter.convertToDatabaseColumn("secret-id"));
        raw[raw.length - 1] ^= 0x01;

        assertThatThrownBy(() -> converter.convertToEntityAttribute(Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Decryption failed");
    }

    @Test
    void wrongKeyFailsLoudly() {
        String stored = converter.convertToDatabaseColumn("secret-id");
        AesGcmConverter other = new AesGcmConverter(new EncryptionProperties(key(2)));

        assertThatThrownBy(() -> other.convertToEntityAttribute(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void badKeysAreRejectedAtStartup() {
        assertThatThrownBy(() -> new AesGcmConverter(new EncryptionProperties(null))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmConverter(new EncryptionProperties("not base64!!"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmConverter(new EncryptionProperties(
                Base64.getEncoder().encodeToString(new byte[16])))).hasMessageContaining("32 bytes");
    }
}
