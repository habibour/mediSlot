package com.medislot.prescription;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.medislot.support.AbstractIntegrationTest;

class PrescriptionIT extends AbstractIntegrationTest {

    private static final Map<String, String> RX = Map.of("medication", "Amoxicillin", "dosage", "500mg", "instructions", "3x daily");

    private long patientId(String token) {
        return call(HttpMethod.GET, "/api/patients/me", token, null).getBody().get("id").asLong();
    }

    private long bookFor(String patient, DoctorFixture doctor, int day, int hour) {
        return book(patient, doctor.id(), slot(day, hour, 0)).getBody().get("id").asLong();
    }

    @Test
    void onlyTreatingDoctorCanPrescribeAndNotOnCancelledAppointments() {
        DoctorFixture doctor = createDoctor();
        DoctorFixture other = createDoctor();
        String patient = registerPatient();
        long appt = bookFor(patient, doctor, 2, 10);
        String url = "/api/appointments/" + appt + "/prescriptions";

        assertThat(call(HttpMethod.POST, url, doctor.token(), RX).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call(HttpMethod.POST, url, other.token(), RX).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, url, patient, RX).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, url, doctor.token(), Map.of("medication", "")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/api/appointments/999999/prescriptions", doctor.token(), RX).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        call(HttpMethod.PATCH, "/api/appointments/" + appt + "/cancel", patient, null);
        assertThat(call(HttpMethod.POST, url, doctor.token(), RX).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void prescriptionsAreVisibleOnlyToOwnerLinkedDoctorAndAdmin() {
        DoctorFixture doctor = createDoctor();
        DoctorFixture unlinked = createDoctor();
        String patient = registerPatient();
        String stranger = registerPatient();
        long pid = patientId(patient);
        long appt = bookFor(patient, doctor, 3, 10);
        call(HttpMethod.POST, "/api/appointments/" + appt + "/prescriptions", doctor.token(), RX);
        String url = "/api/patients/" + pid + "/prescriptions";

        ResponseEntity<JsonNode> own = call(HttpMethod.GET, url, patient, null);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(own.getBody().get("content").get(0).get("medication").asText()).isEqualTo("Amoxicillin");
        assertThat(call(HttpMethod.GET, url, doctor.token(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, url, adminToken(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, url, stranger, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, url, unlinked.token(), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/patients/999999/prescriptions", adminToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void doctorMayReadOnlyPatientsTheyHaveAnAppointmentWith() {
        DoctorFixture doctor = createDoctor();
        String linked = registerPatient();
        String notLinked = registerPatient();
        bookFor(linked, doctor, 4, 10);

        assertThat(call(HttpMethod.GET, "/api/patients/" + patientId(linked), doctor.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/patients/" + patientId(notLinked), doctor.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/patients/" + patientId(notLinked), adminToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
