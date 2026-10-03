package com.medislot.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medislot.appointment.dto.AppointmentResponse;
import com.medislot.appointment.dto.BookAppointmentRequest;
import com.medislot.appointment.event.AppointmentBookedEvent;
import com.medislot.appointment.event.AppointmentCancelledEvent;
import com.medislot.appointment.policy.BookingContext;
import com.medislot.appointment.policy.SlotPolicy;
import com.medislot.common.exception.ResourceNotFoundException;
import com.medislot.common.exception.SlotTakenException;
import com.medislot.doctor.Doctor;
import com.medislot.doctor.DoctorRepository;
import com.medislot.patient.Patient;
import com.medislot.patient.PatientRepository;
import com.medislot.security.AuthenticatedUser;
import com.medislot.user.Role;
import com.medislot.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final List<SlotPolicy> policies;
    private final Clock clock;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public AppointmentService(AppointmentRepository appointments, PatientRepository patients,
            DoctorRepository doctors, List<SlotPolicy> policies, Clock clock,
            UserRepository users, ApplicationEventPublisher events) {
        this.appointments = appointments;
        this.patients = patients;
        this.doctors = doctors;
        this.policies = policies;
        this.clock = clock;
        this.users = users;
        this.events = events;
    }

    /**
     * Locks patient then doctor (always in that order, so concurrent bookings cannot deadlock), then runs the
     * slot policies. The partial unique index and @Version are the last line of defence.
     */
    @Transactional
    public AppointmentResponse book(Long patientUserId, BookAppointmentRequest request) {
        Patient patient = patients.findByUserIdForUpdate(patientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile for user", patientUserId));
        Doctor doctor = doctors.findByIdForUpdate(request.doctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", request.doctorId()));

        Instant start = request.startTime().toInstant();
        Instant end = start.plus(Duration.ofMinutes(doctor.getSlotMinutes()));
        BookingContext ctx = new BookingContext(doctor, patient, start, end, clock.instant());
        policies.forEach(p -> p.check(ctx));

        Appointment saved;
        try {
            saved = appointments.saveAndFlush(
                    new Appointment(patient.getId(), doctor.getId(), start, end, request.reason()));
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            throw new SlotTakenException("The doctor is already booked for that time");
        }
        events.publishEvent(new AppointmentBookedEvent(saved.getId(), saved.getStartTime(),
                emailOf(patient), patient.getPhone()));
        return AppointmentResponse.from(saved);
    }

    @Transactional
    public AppointmentResponse cancel(AuthenticatedUser caller, Long id) {
        Appointment appointment = find(id);
        requireParticipantOrAdmin(caller, appointment);
        appointment.cancel();
        patients.findById(appointment.getPatientId()).ifPresent(patient -> events.publishEvent(
                new AppointmentCancelledEvent(appointment.getId(), appointment.getStartTime(),
                        emailOf(patient), patient.getPhone())));
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse complete(AuthenticatedUser caller, Long id) {
        Appointment appointment = find(id);
        if (caller.role() != Role.DOCTOR || !ownsAsDoctor(caller, appointment)) {
            throw new AccessDeniedException("Only the treating doctor can complete an appointment");
        }
        appointment.complete();
        return AppointmentResponse.from(appointment);
    }

    public Page<Appointment> mine(AuthenticatedUser caller, AppointmentStatus status, Pageable pageable) {
        return switch (caller.role()) {
            case PATIENT -> {
                Long patientId = patientIdOf(caller);
                yield status == null ? appointments.findByPatientId(patientId, pageable)
                        : appointments.findByPatientIdAndStatus(patientId, status, pageable);
            }
            case DOCTOR -> {
                Long doctorId = doctorIdOf(caller);
                yield status == null ? appointments.findByDoctorId(doctorId, pageable)
                        : appointments.findByDoctorIdAndStatus(doctorId, status, pageable);
            }
            case ADMIN -> throw new AccessDeniedException("Admins have no personal appointments");
        };
    }

    private Appointment find(Long id) {
        return appointments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    private void requireParticipantOrAdmin(AuthenticatedUser caller, Appointment appointment) {
        boolean allowed = switch (caller.role()) {
            case ADMIN -> true;
            case PATIENT -> patients.findByUserId(caller.userId())
                    .map(p -> p.getId().equals(appointment.getPatientId())).orElse(false);
            case DOCTOR -> ownsAsDoctor(caller, appointment);
        };
        if (!allowed) {
            throw new AccessDeniedException("Not your appointment");
        }
    }

    private boolean ownsAsDoctor(AuthenticatedUser caller, Appointment appointment) {
        return doctors.findByUserId(caller.userId())
                .map(d -> d.getId().equals(appointment.getDoctorId())).orElse(false);
    }

    private String emailOf(Patient patient) {
        return users.findById(patient.getUserId()).map(u -> u.getEmail()).orElse(null);
    }

    private Long patientIdOf(AuthenticatedUser caller) {
        return patients.findByUserId(caller.userId()).map(Patient::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile for user", caller.userId()));
    }

    private Long doctorIdOf(AuthenticatedUser caller) {
        return doctors.findByUserId(caller.userId()).map(Doctor::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile for user", caller.userId()));
    }
}
