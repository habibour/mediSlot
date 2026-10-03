package com.medislot.appointment;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** True if a BOOKED appointment of the doctor overlaps [start, end). */
    boolean existsByDoctorIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
            Long doctorId, AppointmentStatus status, Instant end, Instant start);

    boolean existsByPatientIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
            Long patientId, AppointmentStatus status, Instant end, Instant start);

    boolean existsByDoctorIdAndPatientId(Long doctorId, Long patientId);

    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

    Page<Appointment> findByPatientIdAndStatus(Long patientId, AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByDoctorId(Long doctorId, Pageable pageable);

    Page<Appointment> findByDoctorIdAndStatus(Long doctorId, AppointmentStatus status, Pageable pageable);
}
