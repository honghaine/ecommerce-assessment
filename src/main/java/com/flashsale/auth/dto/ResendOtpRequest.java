package com.flashsale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendOtpRequest(
        @Schema(example = "buyer@example.com") @NotBlank @Size(max = 254) String identifier) {

    @Override
    public String toString() {
        return "ResendOtpRequest[]";
    }
}
