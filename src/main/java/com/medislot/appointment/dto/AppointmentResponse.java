package com.medislot.appointment.dto;

import java.time.Instant;

import com.medislot.appointment.Appointment;
import com.medislot.appointment.AppointmentStatus;

public record AppointmentResponse(Long id, Long patientId, Long doctorId, Instant startTime, Instant endTime,
        AppointmentStatus status, String reason) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getPatientId(), a.getDoctorId(), a.getStartTime(),
                a.getEndTime(), a.getStatus(), a.getReason());
    }
}
