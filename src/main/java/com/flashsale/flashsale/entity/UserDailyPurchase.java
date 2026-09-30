package com.flashsale.flashsale.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * "1 flash-sale product per user per day": PK (user_id, purchase_date) — the DB rejects a second
 * purchase on the same region-local day, whatever the concurrency.
 */
@Entity
@Table(name = "user_daily_purchases")
@IdClass(UserDailyPurchaseId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyPurchase implements Persistable<UserDailyPurchaseId> {

    public static final String PK_CONSTRAINT = "pk_user_daily_purchases";

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Assigned composite id: always INSERT (the PK violation is the "already bought today" signal), no SELECT first. */
    @Transient
    private boolean isNew = true;

    @Override
    public UserDailyPurchaseId getId() {
        return new UserDailyPurchaseId(userId, purchaseDate);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    public static UserDailyPurchase of(long userId, LocalDate purchaseDate, String region, long orderId) {
        UserDailyPurchase purchase = new UserDailyPurchase();
        purchase.userId = userId;
        purchase.purchaseDate = purchaseDate;
        purchase.region = region;
        purchase.orderId = orderId;
        purchase.createdAt = Instant.now();
        return purchase;
    }
}
