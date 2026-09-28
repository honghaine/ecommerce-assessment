package com.flashsale.notification.scheduler;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.notification.config.NotificationProperties;
import com.flashsale.notification.entity.NotificationOutbox;
import com.flashsale.notification.repository.NotificationOutboxRepository;
import com.flashsale.notification.service.NotificationSender;

/**
 * Drains {@code notification_outbox}. Safe on every instance at once thanks to
 * {@code FOR UPDATE SKIP LOCKED}.
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private final NotificationOutboxRepository repository;
    private final NotificationSender sender;
    private final NotificationProperties properties;
    private final TransactionTemplate transactionTemplate;

    public NotificationDispatcher(NotificationOutboxRepository repository, NotificationSender sender,
                                  NotificationProperties properties, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${app.notification.dispatch-interval-ms:1000}")
    public void scheduledDispatch() {
        if (properties.dispatchEnabled()) {
            dispatchPending();
        }
    }

    /** Row locks are held for the whole batch transaction. @return number of messages processed */
    public int dispatchPending() {
        return transactionTemplate.execute(status -> {
            List<NotificationOutbox> batch = repository.lockPendingBatch(properties.batchSize());
            for (NotificationOutbox message : batch) {
                try {
                    sender.send(message);
                    message.markSent();
                } catch (RuntimeException ex) {
                    log.warn("Notification {} delivery failed", message.getId(), ex);
                    message.markAttemptFailed();
                }
            }
            return batch.size();
        });
    }
}
