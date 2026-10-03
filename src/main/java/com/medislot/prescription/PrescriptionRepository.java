package com.medislot.prescription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    @Query(value = "select r from Prescription r, Appointment a where r.appointmentId = a.id and a.patientId = :patientId",
            countQuery = "select count(r) from Prescription r, Appointment a where r.appointmentId = a.id and a.patientId = :patientId")
    Page<Prescription> findByPatientId(@Param("patientId") Long patientId, Pageable pageable);
}
