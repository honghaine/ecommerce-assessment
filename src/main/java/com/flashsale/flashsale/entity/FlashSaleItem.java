package com.flashsale.flashsale.entity;

import java.math.BigDecimal;
import java.time.Instant;

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

/**
 * A seller's product in a slot with its sale price and quota. {@code sold} is only changed by the
 * atomic conditional UPDATE in {@code FlashSaleItemStockDao}; {@code CHECK (sold <= quota)} backs it.
 */
@Entity
@Table(name = "flash_sale_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashSaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false)
    private int quota;

    @Column(nullable = false, insertable = false, updatable = false)
    private int sold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Auto-approved nomination (approval flow disabled for now). */
    public static FlashSaleItem approved(FlashSaleSession session, long productId, long sellerId,
                                         BigDecimal salePrice, int quota) {
        FlashSaleItem item = new FlashSaleItem();
        item.region = session.getRegion();
        item.sessionId = session.getId();
        item.productId = productId;
        item.sellerId = sellerId;
        item.salePrice = salePrice;
        item.quota = quota;
        item.status = ItemStatus.APPROVED;
        item.createdAt = Instant.now();
        item.updatedAt = item.createdAt;
        return item;
    }
}
