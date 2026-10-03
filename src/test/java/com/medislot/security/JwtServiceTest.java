package com.medislot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medislot.user.Role;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private final JwtService jwt = new JwtService(new JwtProperties(SECRET, 3600));

    @Test
    void roundTripPreservesUserIdAndRole() {
        String token = jwt.generate(42L, Role.DOCTOR);

        assertThat(jwt.parse(token)).contains(new AuthenticatedUser(42L, Role.DOCTOR));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expired = new JwtService(new JwtProperties(SECRET, -10));

        assertThat(jwt.parse(expired.generate(1L, Role.PATIENT))).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService(new JwtProperties("another-secret-another-secret-123456", 3600));

        assertThat(jwt.parse(other.generate(1L, Role.ADMIN))).isEmpty();
    }

    @Test
    void forgedPayloadWithOriginalSignatureIsRejected() {
        String[] parts = jwt.generate(1L, Role.PATIENT).split("\\.");
        String adminPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"exp\":9999999999}".getBytes());

        assertThat(jwt.parse(parts[0] + "." + adminPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", 60)))
                .isInstanceOf(IllegalStateException.class);
    }
}
