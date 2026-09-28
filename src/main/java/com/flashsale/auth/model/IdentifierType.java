package com.flashsale.auth.model;

import com.flashsale.notification.entity.NotificationChannel;

public enum IdentifierType {
    EMAIL(NotificationChannel.EMAIL),
    PHONE(NotificationChannel.SMS);

    private final NotificationChannel channel;

    IdentifierType(NotificationChannel channel) {
        this.channel = channel;
    }

    /** Channel used to deliver OTPs for this identifier type. */
    public NotificationChannel channel() {
        return channel;
    }
}
