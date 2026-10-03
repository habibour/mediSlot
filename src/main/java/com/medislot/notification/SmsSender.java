package com.medislot.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.medislot.common.util.Masking;

/** Stub transport: logs a masked line instead of calling an SMS gateway. */
@Component
public class SmsSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public void send(Notification n) {
        log.info("[stub] SMS to {} subject='{}'", Masking.phone(n.recipient()), n.subject());
    }
}
