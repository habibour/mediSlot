package com.medislot.patient;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.medislot.common.crypto.AesGcmConverter;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private LocalDate dob;

    private String phone;

    /** Stored AES-GCM encrypted; never logged, never indexed, only exposed masked. */
    @Convert(converter = AesGcmConverter.class)
    @Column(name = "national_id_enc")
    private String nationalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Patient() {
    }

    public Patient(Long userId, String fullName, LocalDate dob, String phone, String nationalId) {
        this.userId = userId;
        this.fullName = fullName;
        this.dob = dob;
        this.phone = phone;
        this.nationalId = nationalId;
    }

    /** A null nationalId leaves the stored value untouched. */
    public void update(String fullName, LocalDate dob, String phone, String nationalId) {
        this.fullName = fullName;
        this.dob = dob;
        this.phone = phone;
        if (nationalId != null) {
            this.nationalId = nationalId;
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public LocalDate getDob() { return dob; }
    public String getPhone() { return phone; }
    public String getNationalId() { return nationalId; }
}
