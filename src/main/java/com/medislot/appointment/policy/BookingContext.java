package com.medislot.appointment.policy;

import java.time.Instant;

import com.medislot.doctor.Doctor;
import com.medislot.patient.Patient;

public record BookingContext(Doctor doctor, Patient patient, Instant start, Instant end, Instant now) {
}
