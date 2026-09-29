package com.flashsale.region.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.region.config.RegionProperties;
import com.flashsale.region.service.RegionService;

@Service
public class RegionServiceImpl implements RegionService {

    private final RegionProperties properties;

    public RegionServiceImpl(RegionProperties properties) {
        this.properties = properties;
    }

    /** Normalizes and validates a region code, e.g. {@code "vn"} → {@code "VN"}. */
    @Override
    public String requireSupported(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!properties.regions().containsKey(normalized)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_REGION);
        }
        return normalized;
    }

    @Override
    public ZoneId timezone(String code) {
        return find(code).map(RegionProperties.Region::timezone)
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_REGION));
    }

    /** Region-local calendar date of an instant — the business "day". */
    @Override
    public Currency currency(String code) {
        return find(code).map(RegionProperties.Region::currency)
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_REGION));
    }

    @Override
    public LocalDate localDate(String code, Instant instant) {
        return instant.atZone(timezone(code)).toLocalDate();
    }

    @Override
    public Set<String> codes() {
        return properties.regions().keySet();
    }

    private Optional<RegionProperties.Region> find(String code) {
        return Optional.ofNullable(properties.regions().get(code));
    }
}
