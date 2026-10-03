package com.medislot.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.medislot.common.exception.BadRequestException;
import com.medislot.common.exception.ConflictException;
import com.medislot.common.exception.ResourceNotFoundException;
import com.medislot.doctor.dto.CreateDoctorRequest;
import com.medislot.doctor.dto.DoctorRequest;
import com.medislot.user.Role;
import com.medislot.user.User;
import com.medislot.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock DoctorRepository doctors;
    @Mock UserRepository users;
    @Mock ApplicationEventPublisher events;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private DoctorService service() {
        return new DoctorService(doctors, users, encoder, events);
    }

    private static DoctorRequest profile(String start, String end) {
        return new DoctorRequest(" Dr Heart ", " Cardiology ", null, LocalTime.parse(start), LocalTime.parse(end), 30);
    }

    @Test
    void createMakesDoctorUserWithHashedPasswordAndTrimmedProfile() {
        when(users.existsByEmailIgnoreCase("d@x.com")).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 5L);
            return u;
        });
        when(doctors.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service().create(new CreateDoctorRequest("D@X.com", "Passw0rd1", profile("09:00", "17:00")));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(user.capture());
        assertThat(user.getValue().getRole()).isEqualTo(Role.DOCTOR);
        assertThat(user.getValue().getEmail()).isEqualTo("d@x.com");
        assertThat(encoder.matches("Passw0rd1", user.getValue().getPasswordHash())).isTrue();
        assertThat(response.fullName()).isEqualTo("Dr Heart");
        assertThat(response.specialty()).isEqualTo("Cardiology");
    }

    @Test
    void createRejectsDuplicateEmailAndInvertedHours() {
        when(users.existsByEmailIgnoreCase("dup@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service().create(
                new CreateDoctorRequest("dup@x.com", "Passw0rd1", profile("09:00", "17:00"))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service().create(
                new CreateDoctorRequest("new@x.com", "Passw0rd1", profile("18:00", "17:00"))))
                .isInstanceOf(BadRequestException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void updateOfUnknownDoctorIs404() {
        when(doctors.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(9L, profile("09:00", "17:00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listFiltersBySpecialtyOnlyWhenProvided() {
        service().list(null, Pageable.unpaged());
        service().list("  ", Pageable.unpaged());
        service().list(" cardiology ", Pageable.unpaged());

        verify(doctors, org.mockito.Mockito.times(2)).findAll(any(Pageable.class));
        verify(doctors).findBySpecialtyIgnoreCase("cardiology", Pageable.unpaged());
    }
}
