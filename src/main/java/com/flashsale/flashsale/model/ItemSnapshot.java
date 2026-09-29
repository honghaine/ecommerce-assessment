package com.flashsale.flashsale.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.flashsale.flashsale.entity.ItemStatus;
import com.flashsale.flashsale.entity.SessionStatus;

/** Read-only view of an item + its slot, used for fast pre-checks before the purchase transaction. */
public record ItemSnapshot(long itemId, String region, ItemStatus status, BigDecimal salePrice, long productId,
                           int quota, int sold, long sessionId, LocalDate saleDate, Instant startAt, Instant endAt,
                           SessionStatus sessionStatus) {

    public boolean isLiveAt(Instant now) {
        return status == ItemStatus.APPROVED && sessionStatus != SessionStatus.CANCELLED
                && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public int remaining() {
        return Math.max(0, quota - sold);
    }
}
