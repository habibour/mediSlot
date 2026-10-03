package com.medislot.notification;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.medislot.appointment.event.AppointmentBookedEvent;
import com.medislot.appointment.event.AppointmentCancelledEvent;

/** Observer: reacts only after the booking/cancellation transaction has committed. */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationSenderFactory factory;

    public NotificationListener(NotificationSenderFactory factory) {
        this.factory = factory;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBooked(AppointmentBookedEvent e) {
        notify(e.patientEmail(), e.patientPhone(), "Appointment confirmed",
                "Your appointment #" + e.appointmentId() + " is confirmed for " + e.startTime());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(AppointmentCancelledEvent e) {
        notify(e.patientEmail(), e.patientPhone(), "Appointment cancelled",
                "Your appointment #" + e.appointmentId() + " on " + e.startTime() + " was cancelled");
    }

    private void notify(String email, String phone, String subject, String body) {
        send(Channel.EMAIL, email, subject, body);
        send(Channel.SMS, phone, subject, body);
    }

    private void send(Channel channel, String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        try {
            factory.forChannel(channel).send(Notification.builder().channel(channel).recipient(recipient)
                    .subject(subject).body(body).build());
        } catch (RuntimeException ex) {
            // the booking is already committed; a transport failure must not surface to the caller
            log.error("Notification via {} failed", channel, ex);
        }
    }
}
