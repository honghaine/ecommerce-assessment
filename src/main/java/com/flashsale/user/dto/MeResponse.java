package com.flashsale.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Identifiers are returned masked — the response never carries full PII. */
public record MeResponse(
        @Schema(example = "1") long id,
        @Schema(example = "VN") String region,
        @Schema(example = "USER") String role,
        @Schema(example = "ACTIVE") String status,
        @Schema(example = "b***r@example.com") String email,
        @Schema(example = "+84******678") String phone) {
}
