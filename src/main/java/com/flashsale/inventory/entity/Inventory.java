package com.flashsale.inventory.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stock of a product. {@code available} is sellable outside flash sales; {@code reserved} is
 * allocated to flash-sale quotas. Changed only through conditional UPDATEs in the repository.
 */
@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Inventory open(long productId, String region, int quantity) {
        Inventory inventory = new Inventory();
        inventory.productId = productId;
        inventory.region = region;
        inventory.total = quantity;
        inventory.available = quantity;
        inventory.reserved = 0;
        inventory.updatedAt = Instant.now();
        return inventory;
    }
}
