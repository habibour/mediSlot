package com.medislot.patient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medislot.appointment.AppointmentRepository;
import com.medislot.audit.Audited;
import com.medislot.common.event.PatientChangedEvent;
import com.medislot.common.exception.ResourceNotFoundException;
import com.medislot.doctor.DoctorRepository;
import com.medislot.patient.dto.PatientResponse;
import com.medislot.patient.dto.UpdatePatientRequest;
import com.medislot.security.AuthenticatedUser;
import com.medislot.user.Role;

@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final ApplicationEventPublisher events;

    public PatientService(PatientRepository patients, DoctorRepository doctors, AppointmentRepository appointments,
            ApplicationEventPublisher events) {
        this.patients = patients;
        this.doctors = doctors;
        this.appointments = appointments;
        this.events = events;
    }

    public PatientResponse getMe(Long userId) {
        return PatientResponse.from(findByUser(userId));
    }

    @Transactional
    public PatientResponse updateMe(Long userId, UpdatePatientRequest request) {
        Patient patient = findByUser(userId);
        patient.update(request.fullName().trim(), request.dob(), request.phone(),
                request.nationalId() == null || request.nationalId().isBlank() ? null : request.nationalId().trim());
        events.publishEvent(new PatientChangedEvent(patient.getId(), patient.getFullName()));
        return PatientResponse.from(patient);
    }

    /**
     * Turns search hits into responses under the same visibility rule as {@link #getById}: admins see every hit,
     * a doctor only patients they have an appointment with. Stale index entries are skipped.
     */
    @Audited(action = "SEARCH_PATIENTS", resource = "PATIENT")
    public List<PatientResponse> hydrateForSearch(AuthenticatedUser caller, List<Long> ids) {
        Map<Long, Patient> byId = patients.findAllById(ids).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        Long doctorId = caller.role() == Role.DOCTOR
                ? doctors.findByUserId(caller.userId()).map(d -> d.getId()).orElse(null) : null;
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .filter(p -> caller.role() == Role.ADMIN
                        || (doctorId != null && appointments.existsByDoctorIdAndPatientId(doctorId, p.getId())))
                .map(PatientResponse::from).toList();
    }

    /** Admins see any patient; a doctor only patients they have an appointment with. */
    @Audited(action = "READ_PATIENT", resource = "PATIENT", idArg = 1)
    public PatientResponse getById(AuthenticatedUser caller, Long id) {
        if (caller.role() == Role.DOCTOR) {
            boolean linked = doctors.findByUserId(caller.userId())
                    .map(d -> appointments.existsByDoctorIdAndPatientId(d.getId(), id)).orElse(false);
            if (!linked) {
                throw new AccessDeniedException("No appointment with this patient");
            }
        }
        return PatientResponse.from(patients.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id)));
    }

    public Page<Patient> list(Pageable pageable) {
        return patients.findAll(pageable);
    }

    private Patient findByUser(Long userId) {
        return patients.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile for user", userId));
    }
}
