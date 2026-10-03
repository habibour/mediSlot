package com.medislot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.medislot.common.util.Masking;

class NotificationSenderFactoryTest {

    @Test
    void resolvesSenderByChannel() {
        EmailSender email = new EmailSender();
        SmsSender sms = new SmsSender();
        var factory = new NotificationSenderFactory(List.of(email, sms));

        assertThat(factory.forChannel(Channel.EMAIL)).isSameAs(email);
        assertThat(factory.forChannel(Channel.SMS)).isSameAs(sms);
    }

    @Test
    void unregisteredChannelFailsFast() {
        var factory = new NotificationSenderFactory(List.of(new EmailSender()));

        assertThatThrownBy(() -> factory.forChannel(Channel.SMS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderRequiresChannelAndRecipient() {
        assertThatThrownBy(() -> Notification.builder().recipient("x").build()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Notification.builder().channel(Channel.EMAIL).recipient(" ").build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void maskingHidesPii() {
        assertThat(Masking.email("alice@example.com")).isEqualTo("a***@example.com");
        assertThat(Masking.email("a@x.com")).isEqualTo("***@x.com");
        assertThat(Masking.phone("01712345678")).isEqualTo("*********78");
        assertThat(Masking.phone("12")).isEqualTo("***");
    }
}
