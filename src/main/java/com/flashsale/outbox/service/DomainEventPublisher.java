package com.flashsale.outbox.service;

import java.util.Map;

/**
 * Records a domain event in the outbox. Domain code depends only on this; the transport
 * (DB poller today, Kafka later) is a separate dispatcher.
 */
public interface DomainEventPublisher {

    /** Must run inside the caller's transaction: the event exists iff the business change commits. */
    void publish(String region, String aggregateType, String aggregateId, String eventType,
                 Map<String, Object> payload);
}
