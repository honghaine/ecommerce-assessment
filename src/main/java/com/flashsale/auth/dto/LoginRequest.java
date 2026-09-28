package com.flashsale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "buyer@example.com") @NotBlank @Size(max = 254) String identifier,
        @Schema(example = "Secret123") @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[password=***]";
    }
}
