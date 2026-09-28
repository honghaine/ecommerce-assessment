package com.flashsale.notification.service;

import com.flashsale.notification.entity.NotificationOutbox;

/**
 * Delivery port. Swap the mock for real email/SMS providers without touching callers.
 */
public interface NotificationSender {

    void send(NotificationOutbox message);
}
