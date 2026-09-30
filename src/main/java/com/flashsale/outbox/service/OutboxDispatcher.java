package com.flashsale.outbox.service;

import com.flashsale.outbox.entity.OutboxEvent;

/**
 * Transport seam between the outbox and consumers. Today: in-process handlers in the same database
 * transaction. Later: a Kafka implementation publishing {@code event} keyed by region/aggregate —
 * domain code and handlers stay unchanged.
 */
public interface OutboxDispatcher {

    void dispatch(OutboxEvent event);
}
