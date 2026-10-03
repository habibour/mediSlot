package com.medislot.auth;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medislot.auth.dto.AuthResponse;
import com.medislot.auth.dto.LoginRequest;
import com.medislot.auth.dto.RegisterRequest;
import com.medislot.common.event.PatientChangedEvent;
import com.medislot.common.exception.ConflictException;
import com.medislot.patient.Patient;
import com.medislot.patient.PatientRepository;
import com.medislot.security.JwtService;
import com.medislot.user.Role;
import com.medislot.user.User;
import com.medislot.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PatientRepository patients;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher events;
    /** Compared against when the email is unknown so login timing does not reveal valid accounts. */
    private final String dummyHash;

    public AuthService(UserRepository users, PatientRepository patients, PasswordEncoder passwordEncoder,
            JwtService jwtService, ApplicationEventPublisher events) {
        this.users = users;
        this.patients = patients;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.events = events;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        try {
            User user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), Role.PATIENT));
            Patient patient = patients.save(new Patient(user.getId(), request.fullName().trim(), request.dob(),
                    request.phone(), blankToNull(request.nationalId())));
            events.publishEvent(new PatientChangedEvent(patient.getId(), patient.getFullName()));
        } catch (DataIntegrityViolationException e) {
            // lost a race with a concurrent registration of the same email
            throw new ConflictException("Email is already registered");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches || !user.isEnabled()) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return AuthResponse.bearer(jwtService.generate(user.getId(), user.getRole()),
                jwtService.expirationSeconds(), user.getRole());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
