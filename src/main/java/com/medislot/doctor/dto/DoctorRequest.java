package com.medislot.doctor.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Profile fields shared by create and update. */
public record DoctorRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 100) String specialty,
        @Size(max = 2000) String bio,
        @NotNull LocalTime workingStart,
        @NotNull LocalTime workingEnd,
        @Min(5) @Max(240) int slotMinutes) {
}
