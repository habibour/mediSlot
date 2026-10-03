package com.medislot.common.event;

/** Published after a patient is created or renamed. Deliberately carries the name only (no PII for the index). */
public record PatientChangedEvent(Long patientId, String fullName) {
}
