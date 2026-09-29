package com.flashsale.inventory.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Stock ledger. {@code refEventId} is UNIQUE so an event can change stock only once. */
@Entity
@Table(name = "inventory_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, updatable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MovementReason reason;

    @Column(name = "ref_event_id", updatable = false)
    private UUID refEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static InventoryMovement of(long productId, String region, int delta, MovementReason reason,
                                       UUID refEventId) {
        InventoryMovement movement = new InventoryMovement();
        movement.productId = productId;
        movement.region = region;
        movement.delta = delta;
        movement.reason = reason;
        movement.refEventId = refEventId;
        movement.createdAt = Instant.now();
        return movement;
    }
}
