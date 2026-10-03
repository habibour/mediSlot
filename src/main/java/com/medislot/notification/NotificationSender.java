package com.medislot.notification;

public interface NotificationSender {

    Channel channel();

    void send(Notification notification);
}
