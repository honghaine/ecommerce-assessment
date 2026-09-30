package com.flashsale.flashsale.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

import com.flashsale.flashsale.entity.SellerFlashSaleRule;
import com.flashsale.flashsale.support.DaysOfWeek;

public record RuleResponse(
        @Schema(example = "4") long id,
        @Schema(example = "3") long productId,
        @Schema(example = "12:00", type = "string") LocalTime slotStartTime,
        Set<DayOfWeek> daysOfWeek,
        @Schema(example = "3000000.00") BigDecimal salePrice,
        @Schema(example = "20") int quota,
        @Schema(example = "ACTIVE") String status,
        Instant updatedAt) {

    public static RuleResponse from(SellerFlashSaleRule rule) {
        return new RuleResponse(rule.getId(), rule.getProductId(), rule.getSlotStartTime(),
                DaysOfWeek.fromMask(rule.getDaysOfWeek()), rule.getSalePrice(), rule.getQuota(),
                rule.getStatus().name(), rule.getUpdatedAt());
    }
}
