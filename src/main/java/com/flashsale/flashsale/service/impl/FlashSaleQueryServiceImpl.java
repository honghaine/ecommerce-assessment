package com.flashsale.flashsale.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.flashsale.flashsale.config.FlashSaleProperties;
import com.flashsale.flashsale.dto.CurrentFlashSaleResponse;
import com.flashsale.flashsale.dto.FlashSaleItemView;
import com.flashsale.flashsale.dto.FlashSaleSessionView;
import com.flashsale.flashsale.model.CurrentItemRow;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.service.FlashSaleQueryService;
import com.flashsale.region.service.RegionService;

/**
 * "Live now" listing. Cached per region in Redis for a couple of seconds (shared by all instances),
 * which absorbs refresh storms at slot start; {@code remaining} may lag by up to the TTL.
 */
@Slf4j
@Service
public class FlashSaleQueryServiceImpl implements FlashSaleQueryService {

    private final FlashSaleItemRepository items;
    private final RegionService regionService;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final FlashSaleProperties properties;

    public FlashSaleQueryServiceImpl(FlashSaleItemRepository items, RegionService regionService,
                                     StringRedisTemplate redis, JsonMapper jsonMapper,
                                     FlashSaleProperties properties) {
        this.items = items;
        this.regionService = regionService;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentFlashSaleResponse current(String rawRegion) {
        String region = regionService.requireSupported(rawRegion);
        String cacheKey = "fs:{" + region + "}:current";

        String cached = readCache(cacheKey);
        if (cached != null) {
            return jsonMapper.readValue(cached, CurrentFlashSaleResponse.class);
        }

        Instant now = Instant.now();
        CurrentFlashSaleResponse response = new CurrentFlashSaleResponse(now, region,
                regionService.currency(region).getCurrencyCode(), group(items.findLive(region, now)));
        writeCache(cacheKey, jsonMapper.writeValueAsString(response));
        return response;
    }

    private static List<FlashSaleSessionView> group(List<CurrentItemRow> rows) {
        Map<Long, List<CurrentItemRow>> bySession = new LinkedHashMap<>();
        rows.forEach(row -> bySession.computeIfAbsent(row.sessionId(), id -> new ArrayList<>()).add(row));
        return bySession.values().stream().map(sessionRows -> {
            CurrentItemRow first = sessionRows.getFirst();
            List<FlashSaleItemView> views = sessionRows.stream()
                    .map(row -> new FlashSaleItemView(row.itemId(), row.productId(), row.sku(), row.productName(),
                            row.originalPrice(), row.salePrice(), row.quota(), Math.max(0, row.quota() - row.sold())))
                    .toList();
            return new FlashSaleSessionView(first.sessionId(), first.sessionName(), first.startAt(), first.endAt(),
                    views);
        }).toList();
    }

    private String readCache(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            log.warn("Flash sale cache unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private void writeCache(String key, String json) {
        try {
            redis.opsForValue().set(key, json, properties.currentCacheTtl());
        } catch (DataAccessException ex) {
            log.warn("Flash sale cache unavailable: {}", ex.getMessage());
        }
    }
}
