package com.flashsale.outbox.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.flashsale.outbox.entity.OutboxEvent;
import com.flashsale.outbox.repository.OutboxEventRepository;
import com.flashsale.outbox.service.DomainEventPublisher;

@Service
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxDomainEventPublisher(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String region, String aggregateType, String aggregateId, String eventType,
                        Map<String, Object> payload) {
        repository.save(OutboxEvent.pending(region, aggregateType, aggregateId, eventType,
                jsonMapper.writeValueAsString(payload)));
    }
}
