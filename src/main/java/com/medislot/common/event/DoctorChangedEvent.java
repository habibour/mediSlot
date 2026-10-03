package com.medislot.common.event;

/** Published after a doctor profile is created or updated; carries only what the search index needs. */
public record DoctorChangedEvent(Long doctorId, String fullName, String specialty, String bio) {
}
