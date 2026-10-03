package com.medislot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.medislot.support.AbstractIntegrationTest;

/** Boots the whole app on a throwaway Postgres: proves Flyway migrates and Hibernate validate agrees. */
class MedislotApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoadsAndHealthIsUp() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protectedEndpointRequiresToken() {
        assertThat(rest.getForEntity("/api/doctors", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
