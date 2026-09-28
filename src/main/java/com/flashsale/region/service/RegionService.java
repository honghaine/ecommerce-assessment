package com.flashsale.region.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * Market metadata from config — single source of timezone for business rules.
 */
public interface RegionService {

    /** Normalizes and validates a region code, e.g. {@code "vn"} → {@code "VN"}. */
    String requireSupported(String code);

    ZoneId timezone(String code);

    /** Region-local calendar date of an instant — the business "day". */
    LocalDate localDate(String code, Instant instant);

    Set<String> codes();
}
