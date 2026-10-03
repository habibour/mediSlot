package com.medislot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.medislot.auth.dto.AuthResponse;
import com.medislot.auth.dto.LoginRequest;
import com.medislot.auth.dto.RegisterRequest;
import com.medislot.common.exception.ConflictException;
import com.medislot.patient.Patient;
import com.medislot.patient.PatientRepository;
import com.medislot.security.AuthenticatedUser;
import com.medislot.security.JwtProperties;
import com.medislot.security.JwtService;
import com.medislot.user.Role;
import com.medislot.user.User;
import com.medislot.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock PatientRepository patients;
    @Mock ApplicationEventPublisher events;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwt = new JwtService(new JwtProperties("unit-test-secret-unit-test-secret-1234", 3600));
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, patients, encoder, jwt, events);
    }

    @Test
    void registerStoresBcryptHashNotPlaintextAndCreatesPatientProfile() {
        when(users.existsByEmailIgnoreCase("a@b.com")).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patients.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegisterRequest("A@B.com", "Passw0rd1", " Ann ", null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("a@b.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.PATIENT);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Passw0rd1");
        assertThat(encoder.matches("Passw0rd1", saved.getValue().getPasswordHash())).isTrue();
        ArgumentCaptor<Patient> patient = ArgumentCaptor.forClass(Patient.class);
        verify(patients).save(patient.capture());
        assertThat(patient.getValue().getFullName()).isEqualTo("Ann");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("a@b.com", "Passw0rd1", "Ann", null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void loginReturnsParsableTokenCarryingRole() {
        User user = new User("a@b.com", encoder.encode("Passw0rd1"), Role.DOCTOR);
        ReflectionTestUtils.setField(user, "id", 9L);
        when(users.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));

        AuthResponse response = service.login(new LoginRequest("a@b.com", "Passw0rd1"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.role()).isEqualTo(Role.DOCTOR);
        assertThat(jwt.parse(response.token())).contains(new AuthenticatedUser(9L, Role.DOCTOR));
    }

    @Test
    void loginWithWrongPasswordAndUnknownEmailFailIdentically() {
        User user = new User("a@b.com", encoder.encode("Passw0rd1"), Role.PATIENT);
        when(users.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase("ghost@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("a@b.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@b.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid email or password");
    }
}
