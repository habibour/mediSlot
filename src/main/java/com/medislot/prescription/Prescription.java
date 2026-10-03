package com.medislot.prescription;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(nullable = false)
    private String medication;

    @Column(nullable = false)
    private String dosage;

    private String instructions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Prescription() {
    }

    public Prescription(Long appointmentId, String medication, String dosage, String instructions) {
        this.appointmentId = appointmentId;
        this.medication = medication;
        this.dosage = dosage;
        this.instructions = instructions;
    }

    public Long getId() { return id; }
    public Long getAppointmentId() { return appointmentId; }
    public String getMedication() { return medication; }
    public String getDosage() { return dosage; }
    public String getInstructions() { return instructions; }
    public Instant getCreatedAt() { return createdAt; }
}
