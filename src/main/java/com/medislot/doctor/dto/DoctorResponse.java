package com.medislot.doctor.dto;

import java.time.LocalTime;

import com.medislot.doctor.Doctor;

public record DoctorResponse(Long id, String fullName, String specialty, String bio,
        LocalTime workingStart, LocalTime workingEnd, int slotMinutes) {

    public static DoctorResponse from(Doctor d) {
        return new DoctorResponse(d.getId(), d.getFullName(), d.getSpecialty(), d.getBio(),
                d.getWorkingStart(), d.getWorkingEnd(), d.getSlotMinutes());
    }
}
