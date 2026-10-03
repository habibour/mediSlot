package com.medislot.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.medislot.support.AbstractIntegrationTest;

class AppointmentIT extends AbstractIntegrationTest {

    @Test
    void bookingComputesEndTimeFromDoctorSlotLength() {
        DoctorFixture doctor = createDoctor();
        ResponseEntity<JsonNode> res = book(registerPatient(), doctor.id(), slot(2, 10, 0));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("status").asText()).isEqualTo("BOOKED");
        assertThat(java.time.Instant.parse(res.getBody().get("endTime").asText()))
                .isEqualTo(java.time.Instant.parse(res.getBody().get("startTime").asText()).plusSeconds(30 * 60));
    }

    @Test
    void ruleViolationsAre422NamingThePolicy() {
        DoctorFixture doctor = createDoctor();
        String patient = registerPatient();

        ResponseEntity<JsonNode> early = book(patient, doctor.id(), slot(2, 3, 0));
        ResponseEntity<JsonNode> late = book(patient, doctor.id(), slot(2, 16, 30)); // ends 17:00 -> allowed
        ResponseEntity<JsonNode> overrun = book(patient, doctor.id(), slot(2, 17, 0));
        ResponseEntity<JsonNode> misaligned = book(patient, doctor.id(), slot(2, 10, 10));
        ResponseEntity<JsonNode> past = book(patient, doctor.id(), slot(-1, 10, 0));

        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(early.getBody().get("policy").asText()).isEqualTo("WORKING_HOURS");
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(overrun.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(misaligned.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(past.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void doctorAndPatientOverlapsAreConflicts() {
        DoctorFixture d1 = createDoctor();
        DoctorFixture d2 = createDoctor();
        String p1 = registerPatient();
        String p2 = registerPatient();
        assertThat(book(p1, d1.id(), slot(3, 10, 0)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(book(p2, d1.id(), slot(3, 10, 0)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(book(p1, d2.id(), slot(3, 10, 0)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(book(p1, d2.id(), slot(3, 10, 30)).getStatusCode()).isEqualTo(HttpStatus.CREATED); // adjacent ok
    }

    @Test
    void twentyDifferentPatientsRacingForOneSlotYieldExactlyOneBooking() throws Exception {
        DoctorFixture doctor = createDoctor();
        String start = slot(4, 11, 0);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tokens.add(registerPatient());
        }

        List<HttpStatus> results = race(tokens.stream()
                .<Callable<HttpStatus>>map(t -> () -> HttpStatus.valueOf(
                        book(t, doctor.id(), start).getStatusCode().value()))
                .toList());

        assertThat(results.stream().filter(s -> s == HttpStatus.CREATED)).hasSize(1);
        assertThat(results.stream().filter(s -> s == HttpStatus.CONFLICT)).hasSize(19);
    }

    @Test
    void samePatientSendingTwentyIdenticalRequestsYieldsExactlyOneBooking() throws Exception {
        DoctorFixture doctor = createDoctor();
        String patient = registerPatient();
        String start = slot(4, 12, 0);

        List<HttpStatus> results = race(java.util.stream.IntStream.range(0, 20)
                .<Callable<HttpStatus>>mapToObj(i -> () -> HttpStatus.valueOf(
                        book(patient, doctor.id(), start).getStatusCode().value()))
                .toList());

        assertThat(results.stream().filter(s -> s == HttpStatus.CREATED)).hasSize(1);
        assertThat(results.stream().filter(s -> s == HttpStatus.CONFLICT)).hasSize(19);
    }

    /** Only the patient row lock protects this: different doctors, so no unique index can collide. */
    @Test
    void samePatientRacingAcrossTwentyDoctorsAtTheSameTimeYieldsExactlyOneBooking() throws Exception {
        String patient = registerPatient();
        String start = slot(4, 14, 0);
        List<DoctorFixture> doctors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            doctors.add(createDoctor());
        }

        List<HttpStatus> results = race(doctors.stream()
                .<Callable<HttpStatus>>map(d -> () -> HttpStatus.valueOf(
                        book(patient, d.id(), start).getStatusCode().value()))
                .toList());

        assertThat(results.stream().filter(s -> s == HttpStatus.CREATED)).hasSize(1);
        assertThat(results.stream().filter(s -> s == HttpStatus.CONFLICT)).hasSize(19);
    }

    @Test
    void cancelledSlotCanBeRebookedByAnotherPatient() {
        DoctorFixture doctor = createDoctor();
        String p1 = registerPatient();
        String p2 = registerPatient();
        long id = book(p1, doctor.id(), slot(5, 10, 0)).getBody().get("id").asLong();

        ResponseEntity<JsonNode> cancelled = call(HttpMethod.PATCH, "/api/appointments/" + id + "/cancel", p1, null);
        ResponseEntity<JsonNode> rebooked = book(p2, doctor.id(), slot(5, 10, 0));

        assertThat(cancelled.getBody().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(rebooked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void lifecycleRulesAndOwnership() {
        DoctorFixture doctor = createDoctor();
        DoctorFixture otherDoctor = createDoctor();
        String patient = registerPatient();
        String stranger = registerPatient();
        long id = book(patient, doctor.id(), slot(6, 10, 0)).getBody().get("id").asLong();
        String cancel = "/api/appointments/" + id + "/cancel";
        String complete = "/api/appointments/" + id + "/complete";

        assertThat(call(HttpMethod.PATCH, cancel, stranger, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PATCH, complete, patient, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PATCH, complete, otherDoctor.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PATCH, complete, doctor.token(), null).getBody().get("status").asText())
                .isEqualTo("COMPLETED");
        assertThat(call(HttpMethod.PATCH, cancel, patient, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.PATCH, "/api/appointments/999999/cancel", patient, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mineReturnsOnlyCallersAppointmentsFilteredByStatus() {
        DoctorFixture doctor = createDoctor();
        String patient = registerPatient();
        String other = registerPatient();
        long keep = book(patient, doctor.id(), slot(7, 10, 0)).getBody().get("id").asLong();
        long drop = book(patient, doctor.id(), slot(7, 10, 30)).getBody().get("id").asLong();
        book(other, doctor.id(), slot(7, 11, 0));
        call(HttpMethod.PATCH, "/api/appointments/" + drop + "/cancel", patient, null);

        JsonNode all = call(HttpMethod.GET, "/api/appointments/mine", patient, null).getBody();
        JsonNode booked = call(HttpMethod.GET, "/api/appointments/mine?status=BOOKED", patient, null).getBody();
        JsonNode doctorView = call(HttpMethod.GET, "/api/appointments/mine", doctor.token(), null).getBody();

        assertThat(all.get("totalElements").asInt()).isEqualTo(2);
        assertThat(booked.get("totalElements").asInt()).isEqualTo(1);
        assertThat(booked.get("content").get(0).get("id").asLong()).isEqualTo(keep);
        assertThat(doctorView.get("totalElements").asInt()).isEqualTo(3);
        assertThat(call(HttpMethod.GET, "/api/appointments/mine", adminToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static List<HttpStatus> race(List<Callable<HttpStatus>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<HttpStatus>> futures = tasks.stream()
                    .map(t -> pool.submit(() -> {
                        gate.await();
                        return t.call();
                    })).toList();
            gate.countDown();
            List<HttpStatus> results = new ArrayList<>();
            for (Future<HttpStatus> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
