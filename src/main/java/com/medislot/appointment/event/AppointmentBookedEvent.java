package com.medislot.appointment.event;

import java.time.Instant;

public record AppointmentBookedEvent(Long appointmentId, Instant startTime, String patientEmail, String patientPhone) {
}
