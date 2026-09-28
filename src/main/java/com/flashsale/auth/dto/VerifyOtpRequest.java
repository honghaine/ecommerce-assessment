package com.flashsale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @Schema(example = "buyer@example.com") @NotBlank @Size(max = 254) String identifier,
        @Schema(description = "Code from the mock notification (app log)", example = "123456")
        @NotBlank @Pattern(regexp = "^\\d{4,8}$") String code) {

    @Override
    public String toString() {
        return "VerifyOtpRequest[code=***]";
    }
}
