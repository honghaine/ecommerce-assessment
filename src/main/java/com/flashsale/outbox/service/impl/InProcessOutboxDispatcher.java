package com.flashsale.outbox.service.impl;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.flashsale.outbox.entity.OutboxEvent;
import com.flashsale.outbox.service.EventHandler;
import com.flashsale.outbox.service.OutboxDispatcher;

/**
 * Runs every interested handler inside the caller's transaction. The {@code processed_events} insert and the
 * handler's effect commit or roll back together → an event is never applied twice per consumer, even if it is
 * delivered again (crash before PROCESSED, manual retry, another instance).
 */
@Slf4j
@Component
public class InProcessOutboxDispatcher implements OutboxDispatcher {

    private static final String MARK_PROCESSED =
            "INSERT INTO processed_events (consumer, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING";

    private final List<EventHandler> handlers;
    private final JdbcTemplate jdbc;

    public InProcessOutboxDispatcher(List<EventHandler> handlers, JdbcTemplate jdbc) {
        this.handlers = handlers;
        this.jdbc = jdbc;
    }

    @Override
    public void dispatch(OutboxEvent event) {
        for (EventHandler handler : handlers) {
            if (!handler.supports(event.getEventType())) {
                continue;
            }
            if (jdbc.update(MARK_PROCESSED, handler.consumer(), event.getId()) == 0) {
                log.info("Skipping duplicate event {} ({}) for consumer {}", event.getId(), event.getEventType(),
                        handler.consumer());
                continue;
            }
            handler.handle(event);
        }
    }
}
