package com.flashsale.flashsale.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.common.metrics.BusinessMetrics;
import com.flashsale.flashsale.config.FlashSaleProperties;
import com.flashsale.flashsale.entity.FlashSaleConfig;
import com.flashsale.flashsale.entity.FlashSaleItem;
import com.flashsale.flashsale.entity.FlashSaleSession;
import com.flashsale.flashsale.entity.RuleStatus;
import com.flashsale.flashsale.entity.SellerFlashSaleRule;
import com.flashsale.flashsale.entity.SessionType;
import com.flashsale.flashsale.model.GenerationResult;
import com.flashsale.flashsale.repository.FlashSaleConfigRepository;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.repository.FlashSaleSessionRepository;
import com.flashsale.flashsale.repository.SellerFlashSaleRuleRepository;
import com.flashsale.flashsale.service.SlotGenerationService;
import com.flashsale.inventory.service.InventoryService;
import com.flashsale.region.service.RegionService;

/**
 * For each active day in [today, today + horizon) of the region: ensure every window has a PLATFORM slot
 * (skipping windows already covered by another slot), then add one item per matching ACTIVE seller rule
 * to every slot that has not started yet, reserving the quota from inventory.
 *
 * <p>One transaction per region, serialized across instances by a Postgres advisory lock, so the
 * scheduled run and on-demand runs (after a rule/config change) never race.
 */
@Slf4j
@Service
public class SlotGenerationServiceImpl implements SlotGenerationService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final FlashSaleConfigRepository configs;
    private final FlashSaleSessionRepository sessions;
    private final FlashSaleItemRepository items;
    private final SellerFlashSaleRuleRepository rules;
    private final InventoryService inventoryService;
    private final RegionService regionService;
    private final FlashSaleProperties properties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final BusinessMetrics metrics;

    public SlotGenerationServiceImpl(FlashSaleConfigRepository configs, FlashSaleSessionRepository sessions,
                                     FlashSaleItemRepository items, SellerFlashSaleRuleRepository rules,
                                     InventoryService inventoryService, RegionService regionService,
                                     FlashSaleProperties properties, JdbcTemplate jdbc,
                                     TransactionTemplate transactionTemplate, BusinessMetrics metrics) {
        this.configs = configs;
        this.sessions = sessions;
        this.items = items;
        this.rules = rules;
        this.inventoryService = inventoryService;
        this.regionService = regionService;
        this.properties = properties;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    @Override
    public GenerationResult generate(String region) {
        GenerationResult result = transactionTemplate.execute(status -> {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, "flash-sale-generator:" + region);
            return configs.findById(region)
                    .filter(FlashSaleConfig::isEnabled)
                    .map(config -> generate(region, config))
                    .orElse(GenerationResult.disabled(region));
        });
        metrics.generated(region, result.slotsCreated(), result.itemsCreated(), result.skippedNoStock());
        if (result.slotsCreated() + result.itemsCreated() + result.skippedNoStock() > 0) {
            log.info("Flash sale generation [{}]: {} slots, {} items created, {} skipped (no stock)", region,
                    result.slotsCreated(), result.itemsCreated(), result.skippedNoStock());
        }
        return result;
    }

    private GenerationResult generate(String region, FlashSaleConfig config) {
        ZoneId zone = regionService.timezone(region);
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zone);
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(config.getHorizonDays()).atStartOfDay(zone).toInstant();

        List<FlashSaleSession> existing = new ArrayList<>(sessions.findOverlapping(region, from, to));
        List<SellerFlashSaleRule> activeRules = rules.findByRegionAndStatus(region, RuleStatus.ACTIVE);
        Set<String> existingItems = new HashSet<>();
        if (!existing.isEmpty()) {
            items.findBySessionIdIn(existing.stream().map(FlashSaleSession::getId).toList())
                    .forEach(item -> existingItems.add(item.getSessionId() + ":" + item.getProductId()));
        }

        int slotsCreated = 0, itemsCreated = 0, skippedNoStock = 0;
        for (LocalDate day = today; day.isBefore(today.plusDays(config.getHorizonDays())); day = day.plusDays(1)) {
            if (!config.isActiveOn(day.getDayOfWeek())) {
                continue;
            }
            for (int window = 0; window < config.windowsPerDay(); window++) {
                ZonedDateTime start = day.atStartOfDay(zone).plusMinutes((long) window * config.getSlotMinutes());
                Instant startAt = start.toInstant();
                Instant endAt = start.plusMinutes(config.getSlotMinutes()).toInstant();

                FlashSaleSession session = findExact(existing, startAt, endAt);
                if (session == null) {
                    if (overlapsAny(existing, startAt, endAt)) {
                        continue;   // window already covered by a differently-shaped slot
                    }
                    String name = start.format(HH_MM) + " – " + start.plusMinutes(config.getSlotMinutes()).format(HH_MM);
                    session = sessions.save(FlashSaleSession.generated(region, name, day, startAt, endAt));
                    existing.add(session);
                    slotsCreated++;
                }
                if (!startAt.isAfter(now)) {
                    continue;   // never add items to a slot that has already started
                }
                LocalTime windowStart = start.toLocalTime();
                for (SellerFlashSaleRule rule : activeRules) {
                    if (!rule.appliesTo(day.getDayOfWeek(), windowStart)
                            || !existingItems.add(session.getId() + ":" + rule.getProductId())) {
                        continue;
                    }
                    if (!inventoryService.reserve(rule.getProductId(), region, rule.getQuota())) {
                        skippedNoStock++;
                        existingItems.remove(session.getId() + ":" + rule.getProductId());
                        continue;
                    }
                    items.save(FlashSaleItem.fromRule(session, rule, properties.autoApprove()));
                    itemsCreated++;
                }
            }
        }
        return new GenerationResult(region, slotsCreated, itemsCreated, skippedNoStock);
    }

    private static FlashSaleSession findExact(List<FlashSaleSession> existing, Instant startAt, Instant endAt) {
        return existing.stream()
                .filter(s -> s.getType() == SessionType.PLATFORM && s.getStartAt().equals(startAt)
                        && s.getEndAt().equals(endAt))
                .findFirst().orElse(null);
    }

    private static boolean overlapsAny(List<FlashSaleSession> existing, Instant startAt, Instant endAt) {
        return existing.stream().anyMatch(s -> s.getStartAt().isBefore(endAt) && s.getEndAt().isAfter(startAt));
    }
}
