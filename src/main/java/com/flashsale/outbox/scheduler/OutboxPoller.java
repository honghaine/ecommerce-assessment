package com.flashsale.outbox.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flashsale.outbox.service.impl.OutboxProcessor;

/** Drains the outbox on every instance (SKIP LOCKED makes concurrent pollers safe). */
@Slf4j
@Component
public class OutboxPoller {

    private final OutboxProcessor processor;

    public OutboxPoller(OutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}", initialDelayString = "5000")
    public void poll() {
        try {
            int handled = processor.processBatch();
            if (handled > 0) {
                log.debug("Outbox: handled {} events", handled);
            }
        } catch (RuntimeException ex) {
            log.warn("Outbox poll failed: {}", ex.getMessage());
        }
    }
}
