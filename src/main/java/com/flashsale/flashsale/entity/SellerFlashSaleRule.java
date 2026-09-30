package com.flashsale.flashsale.entity;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

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

import com.flashsale.flashsale.support.DaysOfWeek;

/**
 * Seller's recurring flash-sale plan: "product P in the window starting at {@code slotStartTime}
 * on {@code daysOfWeek}, at {@code salePrice}, {@code quota} units per slot". The generator turns it
 * into concrete {@link FlashSaleItem}s.
 */
@Entity
@Table(name = "seller_flash_sale_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerFlashSaleRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "slot_start_time", nullable = false)
    private LocalTime slotStartTime;

    @Column(name = "days_of_week", nullable = false)
    private short daysOfWeek;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false)
    private int quota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SellerFlashSaleRule create(String region, long sellerId, long productId, LocalTime slotStartTime,
                                             short daysOfWeek, BigDecimal salePrice, int quota) {
        SellerFlashSaleRule rule = new SellerFlashSaleRule();
        rule.region = region;
        rule.sellerId = sellerId;
        rule.productId = productId;
        rule.slotStartTime = slotStartTime;
        rule.daysOfWeek = daysOfWeek;
        rule.salePrice = salePrice;
        rule.quota = quota;
        rule.status = RuleStatus.ACTIVE;
        rule.createdAt = Instant.now();
        rule.updatedAt = rule.createdAt;
        return rule;
    }

    public boolean appliesTo(DayOfWeek day, LocalTime windowStart) {
        return status == RuleStatus.ACTIVE && slotStartTime.equals(windowStart) && DaysOfWeek.contains(daysOfWeek, day);
    }

    public void update(LocalTime slotStartTime, short daysOfWeek, BigDecimal salePrice, int quota) {
        this.slotStartTime = slotStartTime;
        this.daysOfWeek = daysOfWeek;
        this.salePrice = salePrice;
        this.quota = quota;
        touch();
    }

    public void changeStatus(RuleStatus status) {
        this.status = status;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
