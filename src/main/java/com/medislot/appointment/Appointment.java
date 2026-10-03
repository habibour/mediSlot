package com.medislot.appointment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.medislot.common.exception.ConflictException;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    private String reason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Appointment() {
    }

    public Appointment(Long patientId, Long doctorId, Instant startTime, Instant endTime, String reason) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }

    public void cancel() {
        requireBooked("cancel");
        status = AppointmentStatus.CANCELLED;
    }

    public void complete() {
        requireBooked("complete");
        status = AppointmentStatus.COMPLETED;
    }

    private void requireBooked(String action) {
        if (status != AppointmentStatus.BOOKED) {
            throw new ConflictException("Cannot " + action + " an appointment that is " + status);
        }
    }

    public Long getId() { return id; }
    public Long getPatientId() { return patientId; }
    public Long getDoctorId() { return doctorId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public AppointmentStatus getStatus() { return status; }
    public String getReason() { return reason; }
}
