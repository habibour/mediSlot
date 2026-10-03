package com.medislot.appointment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.medislot.appointment.policy.BookingContext;
import com.medislot.appointment.policy.MinLeadTimePolicy;
import com.medislot.appointment.policy.NoOverlapPolicy;
import com.medislot.appointment.policy.WorkingHoursPolicy;
import com.medislot.common.exception.PolicyViolationException;
import com.medislot.common.exception.SlotTakenException;
import com.medislot.config.ClinicProperties;
import com.medislot.doctor.Doctor;
import com.medislot.patient.Patient;

class SlotPolicyTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");
    private static final Instant NOW = ZonedDateTime.of(2030, 1, 1, 8, 0, 0, 0, ZONE).toInstant();

    private final Doctor doctor = doctor(30);
    private final Patient patient = patient();

    private static Doctor doctor(int slot) {
        Doctor d = new Doctor(1L, "Dr X", "Cardiology", null, LocalTime.of(9, 0), LocalTime.of(17, 0), slot);
        ReflectionTestUtils.setField(d, "id", 10L);
        return d;
    }

    private static Patient patient() {
        Patient p = new Patient(2L, "Pat", null, null, null);
        ReflectionTestUtils.setField(p, "id", 20L);
        return p;
    }

    private BookingContext ctx(int hour, int minute) {
        Instant start = ZonedDateTime.of(2030, 1, 2, hour, minute, 0, 0, ZONE).toInstant();
        return new BookingContext(doctor, patient, start, start.plusSeconds(doctor.getSlotMinutes() * 60L), NOW);
    }

    // ---- working hours / alignment
    private final WorkingHoursPolicy hours = new WorkingHoursPolicy(new ClinicProperties(ZONE));

    @Test
    void firstAndLastSlotsOfTheDayAreAllowed() {
        assertThatCode(() -> hours.check(ctx(9, 0))).doesNotThrowAnyException();
        assertThatCode(() -> hours.check(ctx(16, 30))).doesNotThrowAnyException();
    }

    @Test
    void slotsBeforeOpeningOrOverrunningClosingAreRejected() {
        assertThatThrownBy(() -> hours.check(ctx(8, 30))).isInstanceOf(PolicyViolationException.class);
        assertThatThrownBy(() -> hours.check(ctx(17, 0))).isInstanceOf(PolicyViolationException.class);
        assertThatThrownBy(() -> hours.check(ctx(16, 45))).isInstanceOf(PolicyViolationException.class);
    }

    @Test
    void misalignedStartIsRejected() {
        assertThatThrownBy(() -> hours.check(ctx(10, 15)))
                .isInstanceOfSatisfying(PolicyViolationException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getPolicy()).isEqualTo("WORKING_HOURS"));
    }

    @Test
    void hoursAreEvaluatedInClinicZoneNotUtc() {
        // 10:00 Dhaka == 04:00 UTC; must be accepted even though 04:00 is "before 09:00" in UTC
        assertThatCode(() -> hours.check(ctx(10, 0))).doesNotThrowAnyException();
    }

    // ---- lead time
    private final MinLeadTimePolicy lead = new MinLeadTimePolicy();

    @Test
    void exactlyThirtyMinutesAheadIsAllowedButTwentyNineIsNot() {
        Instant ok = NOW.plusSeconds(30 * 60);
        Instant tooSoon = NOW.plusSeconds(29 * 60);

        assertThatCode(() -> lead.check(new BookingContext(doctor, patient, ok, ok.plusSeconds(1800), NOW)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> lead.check(new BookingContext(doctor, patient, tooSoon, tooSoon.plusSeconds(1800), NOW)))
                .isInstanceOf(PolicyViolationException.class);
    }

    // ---- overlap
    private final AppointmentRepository repo = mock(AppointmentRepository.class);
    private final NoOverlapPolicy overlap = new NoOverlapPolicy(repo);

    @Test
    void doctorOverlapIsASlotTakenConflict() {
        when(repo.existsByDoctorIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> overlap.check(ctx(10, 0))).isInstanceOf(SlotTakenException.class);
    }

    @Test
    void patientOverlapIsASlotTakenConflict() {
        when(repo.existsByPatientIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> overlap.check(ctx(10, 0))).isInstanceOf(SlotTakenException.class);
    }

    @Test
    void noOverlapPasses() {
        assertThatCode(() -> overlap.check(ctx(10, 0))).doesNotThrowAnyException();
    }
}
