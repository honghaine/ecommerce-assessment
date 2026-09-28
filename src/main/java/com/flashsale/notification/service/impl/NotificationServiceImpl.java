package com.flashsale.notification.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.flashsale.notification.entity.NotificationChannel;
import com.flashsale.notification.entity.NotificationOutbox;
import com.flashsale.notification.repository.NotificationOutboxRepository;
import com.flashsale.notification.service.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationOutboxRepository repository;
    private final JsonMapper jsonMapper;

    public NotificationServiceImpl(NotificationOutboxRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /** Must join the caller's transaction so the message exists iff the business change commits. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String region, NotificationChannel channel, String recipient, String template,
                        Map<String, Object> payload) {
        repository.save(NotificationOutbox.pending(region, channel, recipient, template,
                jsonMapper.writeValueAsString(payload)));
    }
}
