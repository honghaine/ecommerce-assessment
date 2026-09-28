package com.flashsale.user.config;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Assignment assumption: "users already have a balance" — every new buyer wallet
 * is opened with this demo balance.
 */
@Validated
@ConfigurationProperties("app.wallet")
public record WalletProperties(@NotNull @PositiveOrZero BigDecimal initialBalance) {
}
