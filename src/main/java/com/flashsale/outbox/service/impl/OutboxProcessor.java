package com.flashsale.outbox.service.impl;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.common.metrics.BusinessMetrics;
import com.flashsale.outbox.config.OutboxProperties;
import com.flashsale.outbox.entity.OutboxEvent;
import com.flashsale.outbox.repository.OutboxEventRepository;
import com.flashsale.outbox.service.OutboxDispatcher;

/**
 * Handles one event per transaction: claim (SKIP LOCKED) → dispatch → mark PROCESSED. A failing event rolls back
 * only itself and is rescheduled with backoff in a separate transaction; after max attempts it is FAILED.
 */
@Slf4j
@Component
public class OutboxProcessor {

    private final OutboxEventRepository events;
    private final OutboxDispatcher dispatcher;
    private final OutboxProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final BusinessMetrics metrics;

    public OutboxProcessor(OutboxEventRepository events, OutboxDispatcher dispatcher, OutboxProperties properties,
                           TransactionTemplate transactionTemplate, BusinessMetrics metrics) {
        this.events = events;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    /** @return number of events handled (processed or failed) */
    public int processBatch() {
        int handled = 0;
        while (handled < properties.batchSize() && processNext()) {
            handled++;
        }
        return handled;
    }

    private boolean processNext() {
        UUID[] claimed = new UUID[1];
        try {
            Boolean found = transactionTemplate.execute(status -> events.lockNextDue().map(event -> {
                claimed[0] = event.getId();
                dispatcher.dispatch(event);
                event.markProcessed();
                metrics.outboxProcessed(event.getEventType());
                return true;
            }).orElse(false));
            return Boolean.TRUE.equals(found);
        } catch (RuntimeException ex) {
            if (claimed[0] == null) {
                throw ex;
            }
            recordFailure(claimed[0], ex);
            return true;
        }
    }

    private void recordFailure(UUID eventId, RuntimeException ex) {
        transactionTemplate.executeWithoutResult(status -> events.findById(eventId).ifPresent(event -> {
            event.markAttemptFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage(), properties.maxAttempts());
            metrics.outboxFailed(event.getEventType());
            log.warn("Outbox event {} ({}) failed, attempt {} → {}", eventId, event.getEventType(),
                    event.getAttempts(), event.getStatus(), ex);
        }));
    }
}
