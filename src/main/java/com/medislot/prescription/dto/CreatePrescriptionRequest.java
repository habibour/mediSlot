package com.medislot.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePrescriptionRequest(
        @NotBlank @Size(max = 200) String medication,
        @NotBlank @Size(max = 100) String dosage,
        @Size(max = 2000) String instructions) {
}
