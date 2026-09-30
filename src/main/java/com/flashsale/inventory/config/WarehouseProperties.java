package com.flashsale.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param apiKey shared secret of the warehouse integration; blank disables the endpoint */
@ConfigurationProperties("app.integrations.warehouse")
public record WarehouseProperties(String apiKey) {
}
