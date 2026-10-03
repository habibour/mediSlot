package com.medislot.notification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Factory: resolves the sender for a channel from the registered {@link NotificationSender} beans. */
@Component
public class NotificationSenderFactory {

    private final Map<Channel, NotificationSender> senders = new EnumMap<>(Channel.class);

    public NotificationSenderFactory(List<NotificationSender> available) {
        available.forEach(s -> senders.put(s.channel(), s));
    }

    public NotificationSender forChannel(Channel channel) {
        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            throw new IllegalArgumentException("No sender registered for channel " + channel);
        }
        return sender;
    }
}
