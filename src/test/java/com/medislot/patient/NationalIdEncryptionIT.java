package com.medislot.patient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.medislot.support.AbstractIntegrationTest;

class NationalIdEncryptionIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    private String register(String nationalId) {
        String email = uniqueEmail("nid");
        call(HttpMethod.POST, "/api/auth/register", null, Map.of("email", email, "password", PASSWORD,
                "fullName", "Nid Tester", "nationalId", nationalId));
        return login(email, PASSWORD);
    }

    @Test
    void storedAsCiphertextAndReturnedMasked() {
        String token = register("19901234567890");

        JsonNode me = call(HttpMethod.GET, "/api/patients/me", token, null).getBody();

        assertThat(me.get("nationalId").asText()).isEqualTo("******7890");
        String stored = jdbc.queryForObject("select national_id_enc from patients where id = ?", String.class,
                me.get("id").asLong());
        assertThat(stored).isNotEqualTo("19901234567890").doesNotContain("7890").hasSizeGreaterThan(30);
    }

    @Test
    void updateWithoutNationalIdKeepsExistingAndWithOneReplacesIt() {
        String token = register("11111111");
        Map<String, Object> keep = Map.of("fullName", "Renamed");
        Map<String, Object> replace = Map.of("fullName", "Renamed", "nationalId", "22222222");

        JsonNode kept = call(HttpMethod.PUT, "/api/patients/me", token, keep).getBody();
        JsonNode replaced = call(HttpMethod.PUT, "/api/patients/me", token, replace).getBody();

        assertThat(kept.get("nationalId").asText()).isEqualTo("******1111");
        assertThat(replaced.get("nationalId").asText()).isEqualTo("******2222");
    }

    @Test
    void invalidNationalIdIsRejected() {
        var res = call(HttpMethod.POST, "/api/auth/register", null, Map.of("email", uniqueEmail("bad"),
                "password", PASSWORD, "fullName", "X", "nationalId", "bad id!"));

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().get("errors").has("nationalId")).isTrue();
    }
}
