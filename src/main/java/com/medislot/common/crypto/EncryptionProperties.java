package com.medislot.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code key} is a Base64-encoded 256-bit AES key. */
@ConfigurationProperties(prefix = "app.encryption")
public record EncryptionProperties(String key) {
}
