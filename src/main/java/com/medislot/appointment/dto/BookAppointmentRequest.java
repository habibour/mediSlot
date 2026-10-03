package com.medislot.appointment.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookAppointmentRequest(
        @NotNull Long doctorId,
        @NotNull OffsetDateTime startTime,
        @Size(max = 500) String reason) {
}
