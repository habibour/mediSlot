package com.medislot.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.medislot.common.util.Masking;

/** Stub transport: logs a masked line instead of talking to an SMTP server. */
@Component
public class EmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(Notification n) {
        log.info("[stub] EMAIL to {} subject='{}'", Masking.email(n.recipient()), n.subject());
    }
}
