package com.flashsale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @Schema(description = "Refresh token from login/refresh") @NotBlank @Size(max = 128) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
