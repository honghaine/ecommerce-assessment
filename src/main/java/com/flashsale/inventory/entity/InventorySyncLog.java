package com.flashsale.inventory.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per external warehouse event. {@code UNIQUE (source, external_event_id)} guarantees an event is applied
 * at most once; the stored outcome is replayed when the warehouse re-sends it.
 */
@Entity
@Table(name = "inventory_sync_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventorySyncLog {

    public static final String UNIQUE_CONSTRAINT = "uq_inventory_sync_log_event";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String source;

    @Column(name = "external_event_id", nullable = false, updatable = false)
    private String externalEventId;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(name = "product_id", updatable = false)
    private Long productId;

    @Column(nullable = false, updatable = false)
    private int delta;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private String status;

    @Column(updatable = false)
    private String detail;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public static InventorySyncLog of(String source, String externalEventId, String region, String sku,
                                      Long productId, int delta, String reason, String status, String detail) {
        InventorySyncLog log = new InventorySyncLog();
        log.source = source;
        log.externalEventId = externalEventId;
        log.region = region;
        log.sku = sku;
        log.productId = productId;
        log.delta = delta;
        log.reason = reason;
        log.status = status;
        log.detail = detail;
        log.receivedAt = Instant.now();
        return log;
    }
}
