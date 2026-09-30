package com.flashsale.outbox.service;

import com.flashsale.outbox.entity.OutboxEvent;

/**
 * Consumer of outbox events. Delivery is at-least-once; the dispatcher records
 * {@code (consumer, event_id)} in {@code processed_events} in the same transaction, so each handler
 * applies an event's effect exactly once. Handlers must be transactional-safe (no external side effects).
 */
public interface EventHandler {

    /** Stable consumer name — part of the dedupe key; never rename once events were processed. */
    String consumer();

    boolean supports(String eventType);

    void handle(OutboxEvent event);
}
