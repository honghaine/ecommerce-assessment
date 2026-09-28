package com.flashsale.notification.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.flashsale.common.logging.PiiMasker;
import com.flashsale.notification.config.NotificationProperties;
import com.flashsale.notification.entity.NotificationOutbox;
import com.flashsale.notification.service.NotificationSender;

/**
 * Mock delivery: writes the message to the log. Recipient is always masked;
 * content is logged only when {@code app.notification.mock-log-content=true} (dev).
 */
@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

    private final NotificationProperties properties;

    public LogNotificationSender(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(NotificationOutbox message) {
        if (properties.mockLogContent()) {
            log.info("[MOCK {}] to={} template={} content={}", message.getChannel(),
                    PiiMasker.mask(message.getRecipient()), message.getTemplate(), message.getPayload());
        } else {
            log.info("[MOCK {}] to={} template={}", message.getChannel(),
                    PiiMasker.mask(message.getRecipient()), message.getTemplate());
        }
    }
}
