package com.flashsale.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(description = "Email, or phone in international format", example = "buyer@example.com")
        @NotBlank @Size(max = 254) String identifier,
        @Schema(description = "8–72 chars, at least one letter and one digit", example = "Secret123")
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain a letter and a digit")
        String password,
        @Schema(description = "Market code", example = "SG", allowableValues = {"MY","CN", "SG"})
        @NotBlank @Size(max = 8) String region) {

    @Override
    public String toString() {
        return "RegisterRequest[region=" + region + ", password=***]";
    }
}
