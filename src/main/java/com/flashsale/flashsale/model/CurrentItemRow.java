package com.flashsale.flashsale.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Flat row of the "live now" query; grouped into sessions by the query service. */
public record CurrentItemRow(long sessionId, String sessionName, Instant startAt, Instant endAt,
                             long itemId, long productId, String sku, String productName,
                             BigDecimal originalPrice, BigDecimal salePrice, int quota, int sold) {
}
