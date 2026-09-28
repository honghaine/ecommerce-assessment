package com.flashsale.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.IntegrationTest;
import com.flashsale.notification.entity.NotificationChannel;
import com.flashsale.notification.entity.NotificationOutbox;
import com.flashsale.notification.scheduler.NotificationDispatcher;
import com.flashsale.notification.service.NotificationService;

class NotificationDispatcherIntegrationTest extends IntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void dispatchMarksSentAndRedactsPayload() {
        String recipient = "dispatch-" + System.nanoTime() + "@example.com";
        transactionTemplate.executeWithoutResult(s -> notificationService.enqueue("VN", NotificationChannel.EMAIL,
                recipient, "OTP_REGISTER", Map.of("code", "123456")));

        while (dispatcher.dispatchPending() > 0) {
            // drain everything pending, including rows left by other tests
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, payload::text AS payload, sent_at FROM notification_outbox WHERE recipient = ?",
                recipient);
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("sent_at")).isNotNull();
        assertThat((String) row.get("payload")).doesNotContain("123456")
                .isEqualToIgnoringWhitespace(NotificationOutbox.REDACTED_PAYLOAD);
    }
}
