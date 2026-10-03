package com.medislot.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.medislot.support.AbstractIntegrationTest;

/**
 * Asserts on what is physically stored, not on what the API round-trips: a symmetric time-zone shift on write and
 * read is invisible to API tests but corrupts the data for everyone who reads the database directly.
 */
class StoredValuesIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void workingHoursAreStoredAsWrittenNotShifted() {
        DoctorFixture doctor = createDoctor();

        String start = jdbc.queryForObject("select cast(working_start as char(8)) from doctors where id = ?",
                String.class, doctor.id());
        String end = jdbc.queryForObject("select cast(working_end as char(8)) from doctors where id = ?",
                String.class, doctor.id());

        assertThat(start).isEqualTo("09:00:00");
        assertThat(end).isEqualTo("17:00:00");
    }

    @Test
    void dateOfBirthIsStoredAsWritten() {
        String token = registerPatient("Dob Tester", Map.of("dob", "1990-05-17"));
        JsonNode me = call(HttpMethod.GET, "/api/patients/me", token, null).getBody();

        String stored = jdbc.queryForObject("select cast(dob as char(10)) from patients where id = ?",
                String.class, me.get("id").asLong());

        assertThat(stored).isEqualTo("1990-05-17");
        assertThat(me.get("dob").asText()).isEqualTo("1990-05-17");
    }

    @Test
    void appointmentInstantIsStoredAsUtc() {
        DoctorFixture doctor = createDoctor();
        String patient = registerPatient();
        String start = slot(3, 10, 0); // 10:00 clinic time (UTC+6) == 04:00 UTC

        long id = book(patient, doctor.id(), start).getBody().get("id").asLong();

        String stored = jdbc.queryForObject("select cast(start_time as char(19)) from appointments where id = ?",
                String.class, id);
        assertThat(stored).endsWith("04:00:00");
    }

    @Test
    void jvmRunsInUtc() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }
}
