package com.medislot.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.medislot.appointment.event.AppointmentBookedEvent;
import com.medislot.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationIT extends AbstractIntegrationTest {

    @MockitoBean NotificationSenderFactory factory;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager txManager;

    private final NotificationSender sender = org.mockito.Mockito.mock(NotificationSender.class);

    @BeforeEach
    void stub() {
        when(factory.forChannel(any())).thenReturn(sender);
    }

    @Test
    void successfulBookingNotifiesAfterCommit() {
        DoctorFixture doctor = createDoctor();

        book(registerPatient(), doctor.id(), slot(2, 10, 0));

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(sender, timeout(2000)).send(sent.capture());
        assertThat(sent.getValue().channel()).isEqualTo(Channel.EMAIL);
        assertThat(sent.getValue().subject()).isEqualTo("Appointment confirmed");
    }

    @Test
    void cancellationNotifies() {
        DoctorFixture doctor = createDoctor();
        String patient = registerPatient();
        long id = book(patient, doctor.id(), slot(2, 11, 0)).getBody().get("id").asLong();
        verify(sender, timeout(2000)).send(any());

        call(HttpMethod.PATCH, "/api/appointments/" + id + "/cancel", patient, null);

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(sender, timeout(2000).times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(1).subject()).isEqualTo("Appointment cancelled");
    }

    @Test
    void rolledBackTransactionSendsNothingButCommittedOneDoes() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        var event = new AppointmentBookedEvent(1L, Instant.now(), "a@b.com", null);

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(event);
            status.setRollbackOnly();
        });
        verify(sender, never()).send(any());

        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        verify(sender).send(any());
    }

    @Test
    void failedBookingSendsNothing() {
        DoctorFixture doctor = createDoctor();

        book(registerPatient(), doctor.id(), slot(2, 3, 0)); // outside working hours -> 422

        verify(sender, never()).send(any());
    }
}
