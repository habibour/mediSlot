package com.medislot.prescription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medislot.appointment.Appointment;
import com.medislot.appointment.AppointmentRepository;
import com.medislot.appointment.AppointmentStatus;
import com.medislot.audit.Audited;
import com.medislot.common.exception.ConflictException;
import com.medislot.common.exception.ResourceNotFoundException;
import com.medislot.doctor.DoctorRepository;
import com.medislot.patient.PatientRepository;
import com.medislot.prescription.dto.CreatePrescriptionRequest;
import com.medislot.prescription.dto.PrescriptionResponse;
import com.medislot.security.AuthenticatedUser;
import com.medislot.user.Role;

@Service
@Transactional(readOnly = true)
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final PatientRepository patients;

    public PrescriptionService(PrescriptionRepository prescriptions, AppointmentRepository appointments,
            DoctorRepository doctors, PatientRepository patients) {
        this.prescriptions = prescriptions;
        this.appointments = appointments;
        this.doctors = doctors;
        this.patients = patients;
    }

    @Transactional
    @Audited(action = "ADD_PRESCRIPTION", resource = "APPOINTMENT", idArg = 1)
    public PrescriptionResponse add(AuthenticatedUser caller, Long appointmentId, CreatePrescriptionRequest request) {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        boolean treating = doctors.findByUserId(caller.userId())
                .map(d -> d.getId().equals(appointment.getDoctorId())).orElse(false);
        if (caller.role() != Role.DOCTOR || !treating) {
            throw new AccessDeniedException("Only the treating doctor can prescribe");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ConflictException("Cannot prescribe against a cancelled appointment");
        }
        return PrescriptionResponse.from(prescriptions.save(new Prescription(appointmentId,
                request.medication().trim(), request.dosage().trim(), request.instructions())));
    }

    @Audited(action = "READ_PRESCRIPTIONS", resource = "PATIENT", idArg = 1)
    public Page<Prescription> listForPatient(AuthenticatedUser caller, Long patientId, Pageable pageable) {
        switch (caller.role()) {
            case PATIENT -> {
                boolean self = patients.findByUserId(caller.userId())
                        .map(p -> p.getId().equals(patientId)).orElse(false);
                if (!self) {
                    throw new AccessDeniedException("Not your prescriptions");
                }
            }
            case DOCTOR -> {
                boolean linked = doctors.findByUserId(caller.userId())
                        .map(d -> appointments.existsByDoctorIdAndPatientId(d.getId(), patientId)).orElse(false);
                if (!linked) {
                    throw new AccessDeniedException("No appointment with this patient");
                }
            }
            case ADMIN -> {
                if (!patients.existsById(patientId)) {
                    throw new ResourceNotFoundException("Patient", patientId);
                }
            }
        }
        return prescriptions.findByPatientId(patientId, pageable);
    }
}
