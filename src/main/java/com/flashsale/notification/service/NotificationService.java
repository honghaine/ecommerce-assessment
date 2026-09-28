package com.flashsale.notification.service;

import java.util.Map;

import com.flashsale.notification.entity.NotificationChannel;

/**
 * Queues outgoing messages in the notification outbox.
 */
public interface NotificationService {

    /** Must be called inside the caller's transaction so the message commits with the business change. */
    void enqueue(String region, NotificationChannel channel, String recipient, String template,
                 Map<String, Object> payload);
}
