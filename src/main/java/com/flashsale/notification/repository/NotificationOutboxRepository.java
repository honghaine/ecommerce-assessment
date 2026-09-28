package com.flashsale.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.flashsale.notification.entity.NotificationOutbox;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    /** SKIP LOCKED lets several instances drain the outbox in parallel without double-sending. */
    @Query(value = """
            SELECT * FROM notification_outbox
            WHERE status = 'PENDING'
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationOutbox> lockPendingBatch(int limit);
}
