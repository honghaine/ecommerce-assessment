package com.flashsale.user.entity;

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

/** Balance ledger row. */
@Entity
@Table(name = "wallet_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private WalletTransactionType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static WalletTransaction debitForOrder(long userId, String region, long orderId, BigDecimal amount) {
        WalletTransaction tx = new WalletTransaction();
        tx.userId = userId;
        tx.region = region;
        tx.orderId = orderId;
        tx.amount = amount;
        tx.type = WalletTransactionType.DEBIT;
        tx.createdAt = Instant.now();
        return tx;
    }
}
