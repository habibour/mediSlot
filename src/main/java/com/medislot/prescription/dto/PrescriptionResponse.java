package com.medislot.prescription.dto;

import java.time.Instant;

import com.medislot.prescription.Prescription;

public record PrescriptionResponse(Long id, Long appointmentId, String medication, String dosage,
        String instructions, Instant createdAt) {

    public static PrescriptionResponse from(Prescription p) {
        return new PrescriptionResponse(p.getId(), p.getAppointmentId(), p.getMedication(), p.getDosage(),
                p.getInstructions(), p.getCreatedAt());
    }
}
