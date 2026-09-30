package com.flashsale.outbox.dto;

import java.time.Instant;
import java.util.UUID;

import com.flashsale.outbox.entity.OutboxEvent;

public record OutboxEventView(UUID id, String eventType, String aggregateType, String aggregateId, String status,
                              int attempts, String lastError, Instant createdAt, Instant nextAttemptAt,
                              Instant processedAt) {

    public static OutboxEventView from(OutboxEvent event) {
        return new OutboxEventView(event.getId(), event.getEventType(), event.getAggregateType(),
                event.getAggregateId(), event.getStatus().name(), event.getAttempts(), event.getLastError(),
                event.getCreatedAt(), event.getNextAttemptAt(), event.getProcessedAt());
    }
}
