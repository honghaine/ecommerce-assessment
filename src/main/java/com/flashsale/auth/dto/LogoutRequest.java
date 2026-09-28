package com.flashsale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Schema(description = "Optional: also revoke this refresh token") @Size(max = 128) String refreshToken) {

    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=***]";
    }
}
