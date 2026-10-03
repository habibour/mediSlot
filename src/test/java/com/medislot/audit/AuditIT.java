package com.medislot.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.medislot.support.AbstractIntegrationTest;

class AuditIT extends AbstractIntegrationTest {

    private JsonNode logs(String query) {
        return call(HttpMethod.GET, "/api/admin/audit-logs?" + query, adminToken(), null).getBody();
    }

    @Test
    void patientDataAccessIsAuditedWithActorAndResource() {
        DoctorFixture doctor = createDoctor();
        String patient = registerPatient();
        long pid = call(HttpMethod.GET, "/api/patients/me", patient, null).getBody().get("id").asLong();
        long appt = book(patient, doctor.id(), slot(2, 10, 0)).getBody().get("id").asLong();

        call(HttpMethod.GET, "/api/patients/" + pid, doctor.token(), null);
        call(HttpMethod.POST, "/api/appointments/" + appt + "/prescriptions", doctor.token(),
                java.util.Map.of("medication", "X", "dosage", "1"));
        call(HttpMethod.GET, "/api/patients/" + pid + "/prescriptions", patient, null);

        JsonNode patientRows = logs("resourceType=PATIENT&resourceId=" + pid).get("content");
        assertThat(patientRows).hasSize(2);
        assertThat(patientRows).extracting(n -> n.get("action").asText())
                .containsExactlyInAnyOrder("READ_PATIENT", "READ_PRESCRIPTIONS");
        assertThat(patientRows).extracting(n -> n.get("actorRole").asText())
                .containsExactlyInAnyOrder("DOCTOR", "PATIENT");
        JsonNode apptRows = logs("resourceType=APPOINTMENT&resourceId=" + appt).get("content");
        assertThat(apptRows).hasSize(1);
        assertThat(apptRows.get(0).get("action").asText()).isEqualTo("ADD_PRESCRIPTION");
    }

    @Test
    void deniedAccessLeavesNoAuditRow() {
        DoctorFixture unlinked = createDoctor();
        String patient = registerPatient();
        long pid = call(HttpMethod.GET, "/api/patients/me", patient, null).getBody().get("id").asLong();

        assertThat(call(HttpMethod.GET, "/api/patients/" + pid, unlinked.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(logs("resourceType=PATIENT&resourceId=" + pid).get("content")).isEmpty();
    }

    @Test
    void auditLogsAreAdminOnlyAndFilterableByActorAndTime() {
        String patient = registerPatient();
        assertThat(call(HttpMethod.GET, "/api/admin/audit-logs", patient, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/admin/audit-logs", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        long pid = call(HttpMethod.GET, "/api/patients/me", patient, null).getBody().get("id").asLong();
        call(HttpMethod.GET, "/api/patients/" + pid + "/prescriptions", patient, null);
        JsonNode row = logs("resourceType=PATIENT&resourceId=" + pid).get("content").get(0);
        long actor = row.get("actorUserId").asLong();

        assertThat(logs("actorUserId=" + actor + "&resourceType=PATIENT").get("content")).isNotEmpty();
        assertThat(logs("actorUserId=" + actor + "&to=2000-01-01T00:00:00Z").get("content")).isEmpty();
        assertThat(logs("actorUserId=" + actor + "&from=2000-01-01T00:00:00Z").get("content")).isNotEmpty();
    }
}
