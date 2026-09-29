package com.flashsale.flashsale.model;

import java.math.BigDecimal;

import com.flashsale.order.entity.Order;

/** @param replayed true when an earlier order was returned for the same Idempotency-Key */
public record PurchaseResult(Order order, BigDecimal balance, boolean replayed) {
}
