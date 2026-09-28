package com.flashsale.user.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Wallet open(User user, BigDecimal initialBalance) {
        Wallet wallet = new Wallet();
        wallet.userId = user.getId();
        wallet.region = user.getRegion();
        wallet.balance = initialBalance;
        wallet.updatedAt = Instant.now();
        return wallet;
    }
}
