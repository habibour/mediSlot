package com.medislot.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePatientRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Past LocalDate dob,
        @Size(max = 30) String phone,
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "may contain letters, digits and dashes only") String nationalId) {
}
