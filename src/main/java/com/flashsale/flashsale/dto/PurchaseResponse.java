package com.flashsale.flashsale.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.flashsale.flashsale.model.PurchaseResult;

public record PurchaseResponse(
        @Schema(example = "101") long orderId,
        @Schema(example = "12") long itemId,
        @Schema(example = "3") long productId,
        @Schema(example = "5000000.00") BigDecimal amount,
        @Schema(example = "VND") String currency,
        @Schema(example = "PAID") String status,
        Instant purchasedAt,
        @Schema(description = "Wallet balance after the purchase", example = "5000000.00") BigDecimal balance) {

    public static PurchaseResponse from(PurchaseResult result, String currency) {
        var order = result.order();
        return new PurchaseResponse(order.getId(), order.getFlashSaleItemId(), order.getProductId(),
                order.getAmount(), currency, order.getStatus().name(), order.getCreatedAt(), result.balance());
    }
}
