package com.flashsale.outbox.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.flashsale.outbox.entity.OutboxEvent;
import com.flashsale.outbox.entity.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims the oldest due event. SKIP LOCKED lets every instance poll concurrently without two of them
     * ever handling the same event at the same time.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= now()
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvent> lockNextDue();

    List<OutboxEvent> findByRegionAndStatusOrderByCreatedAtDesc(String region, OutboxStatus status, Limit limit);

    long countByRegionAndStatus(String region, OutboxStatus status);
}
