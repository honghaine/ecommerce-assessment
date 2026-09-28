package com.flashsale.notification.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Outgoing message, written in the same transaction as the business change and
 * delivered asynchronously. The payload is redacted once the message is sent.
 */
@Entity
@Table(name = "notification_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    public static final String REDACTED_PAYLOAD = "{\"redacted\":true}";
    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationChannel channel;

    @Column(nullable = false, updatable = false)
    private String recipient;

    @Column(nullable = false, updatable = false)
    private String template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static NotificationOutbox pending(String region, NotificationChannel channel, String recipient,
                                             String template, String payloadJson) {
        NotificationOutbox message = new NotificationOutbox();
        message.region = region;
        message.channel = channel;
        message.recipient = recipient;
        message.template = template;
        message.payload = payloadJson;
        message.status = NotificationStatus.PENDING;
        message.createdAt = Instant.now();
        return message;
    }

    public void markSent() {
        status = NotificationStatus.SENT;
        sentAt = Instant.now();
        payload = REDACTED_PAYLOAD;
    }

    public void markAttemptFailed() {
        attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            status = NotificationStatus.FAILED;
            payload = REDACTED_PAYLOAD;
        }
    }
}
