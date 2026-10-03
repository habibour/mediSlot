package com.medislot.doctor;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String specialty;

    private String bio;

    @Column(name = "working_start", nullable = false)
    private LocalTime workingStart;

    @Column(name = "working_end", nullable = false)
    private LocalTime workingEnd;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    protected Doctor() {
    }

    public Doctor(Long userId, String fullName, String specialty, String bio,
            LocalTime workingStart, LocalTime workingEnd, int slotMinutes) {
        this.userId = userId;
        update(fullName, specialty, bio, workingStart, workingEnd, slotMinutes);
    }

    public void update(String fullName, String specialty, String bio,
            LocalTime workingStart, LocalTime workingEnd, int slotMinutes) {
        this.fullName = fullName;
        this.specialty = specialty;
        this.bio = bio;
        this.workingStart = workingStart;
        this.workingEnd = workingEnd;
        this.slotMinutes = slotMinutes;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getSpecialty() { return specialty; }
    public String getBio() { return bio; }
    public LocalTime getWorkingStart() { return workingStart; }
    public LocalTime getWorkingEnd() { return workingEnd; }
    public int getSlotMinutes() { return slotMinutes; }
}
