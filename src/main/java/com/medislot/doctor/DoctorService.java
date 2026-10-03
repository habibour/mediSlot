package com.medislot.doctor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medislot.common.exception.BadRequestException;
import com.medislot.common.exception.ConflictException;
import com.medislot.common.event.DoctorChangedEvent;
import com.medislot.common.exception.ResourceNotFoundException;
import com.medislot.doctor.dto.CreateDoctorRequest;
import com.medislot.doctor.dto.DoctorRequest;
import com.medislot.doctor.dto.DoctorResponse;
import com.medislot.user.Role;
import com.medislot.user.User;
import com.medislot.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class DoctorService {

    private final DoctorRepository doctors;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public DoctorService(DoctorRepository doctors, UserRepository users, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events) {
        this.doctors = doctors;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        DoctorRequest p = request.profile();
        validateHours(p);
        try {
            User user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), Role.DOCTOR));
            Doctor doctor = doctors.save(new Doctor(user.getId(), p.fullName().trim(), p.specialty().trim(),
                    p.bio(), p.workingStart(), p.workingEnd(), p.slotMinutes()));
            publishChanged(doctor);
            return DoctorResponse.from(doctor);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Email is already registered");
        }
    }

    @Transactional
    public DoctorResponse update(Long id, DoctorRequest p) {
        validateHours(p);
        Doctor doctor = find(id);
        doctor.update(p.fullName().trim(), p.specialty().trim(), p.bio(),
                p.workingStart(), p.workingEnd(), p.slotMinutes());
        publishChanged(doctor);
        return DoctorResponse.from(doctor);
    }

    /** Resolves ids in the given order; ids missing from the database (stale index entries) are skipped. */
    public List<DoctorResponse> getByIds(List<Long> ids) {
        Map<Long, Doctor> byId = doctors.findAllById(ids).stream()
                .collect(Collectors.toMap(Doctor::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).map(DoctorResponse::from).toList();
    }

    public DoctorResponse get(Long id) {
        return DoctorResponse.from(find(id));
    }

    public Page<Doctor> list(String specialty, Pageable pageable) {
        return (specialty == null || specialty.isBlank())
                ? doctors.findAll(pageable)
                : doctors.findBySpecialtyIgnoreCase(specialty.trim(), pageable);
    }

    private void publishChanged(Doctor d) {
        events.publishEvent(new DoctorChangedEvent(d.getId(), d.getFullName(), d.getSpecialty(), d.getBio()));
    }

    private Doctor find(Long id) {
        return doctors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
    }

    private static void validateHours(DoctorRequest p) {
        if (!p.workingStart().isBefore(p.workingEnd())) {
            throw new BadRequestException("workingStart must be before workingEnd");
        }
    }
}
