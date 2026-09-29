package com.flashsale.flashsale.entity;

import java.io.Serializable;
import java.time.LocalDate;

public record UserDailyPurchaseId(Long userId, LocalDate purchaseDate) implements Serializable {

    public UserDailyPurchaseId() {
        this(null, null);
    }
}
