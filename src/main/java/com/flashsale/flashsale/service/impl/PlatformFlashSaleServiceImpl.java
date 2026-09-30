package com.flashsale.flashsale.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.catalog.entity.Product;
import com.flashsale.catalog.repository.ProductRepository;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.flashsale.dto.AdminSessionView;
import com.flashsale.flashsale.dto.FlashSaleConfigRequest;
import com.flashsale.flashsale.dto.FlashSaleConfigResponse;
import com.flashsale.flashsale.entity.FlashSaleConfig;
import com.flashsale.flashsale.entity.FlashSaleItem;
import com.flashsale.flashsale.entity.FlashSaleSession;
import com.flashsale.flashsale.model.GenerationResult;
import com.flashsale.flashsale.repository.FlashSaleConfigRepository;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.repository.FlashSaleSessionRepository;
import com.flashsale.flashsale.service.PlatformFlashSaleService;
import com.flashsale.flashsale.service.SlotGenerationService;
import com.flashsale.flashsale.support.DaysOfWeek;
import com.flashsale.region.service.RegionService;

@Service
public class PlatformFlashSaleServiceImpl implements PlatformFlashSaleService {

    private final FlashSaleConfigRepository configs;
    private final FlashSaleSessionRepository sessions;
    private final FlashSaleItemRepository items;
    private final ProductRepository products;
    private final SlotGenerationService generator;
    private final RegionService regionService;
    private final TransactionTemplate transactionTemplate;

    public PlatformFlashSaleServiceImpl(FlashSaleConfigRepository configs, FlashSaleSessionRepository sessions,
                                        FlashSaleItemRepository items, ProductRepository products,
                                        SlotGenerationService generator, RegionService regionService,
                                        TransactionTemplate transactionTemplate) {
        this.configs = configs;
        this.sessions = sessions;
        this.items = items;
        this.products = products;
        this.generator = generator;
        this.regionService = regionService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public FlashSaleConfigResponse getConfig(String region) {
        return FlashSaleConfigResponse.from(requireConfig(region), regionService.timezone(region).getId());
    }

    @Override
    public FlashSaleConfigResponse updateConfig(String region, long adminId, FlashSaleConfigRequest request) {
        if (1440 % request.slotMinutes() != 0) {
            throw new ApiException(ErrorCode.INVALID_SLOT_LENGTH);
        }
        FlashSaleConfigResponse response = transactionTemplate.execute(status -> {
            FlashSaleConfig config = requireConfig(region);
            config.update(request.enabled(), request.slotMinutes(), DaysOfWeek.toMask(request.activeDays()),
                    request.horizonDays(), adminId);
            return FlashSaleConfigResponse.from(configs.saveAndFlush(config), regionService.timezone(region).getId());
        });
        generator.generate(region);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminSessionView> listSessions(String region, LocalDate date) {
        ZoneId zone = regionService.timezone(region);
        LocalDate day = date != null ? date : LocalDate.now(zone);
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();

        List<FlashSaleSession> slots = sessions.findOverlapping(region, from, to);
        if (slots.isEmpty()) {
            return List.of();
        }
        List<FlashSaleItem> slotItems = items.findBySessionIdIn(slots.stream().map(FlashSaleSession::getId).toList());
        Map<Long, Product> productById = products.findAllById(slotItems.stream().map(FlashSaleItem::getProductId)
                .distinct().toList()).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, List<FlashSaleItem>> itemsBySession = slotItems.stream()
                .collect(Collectors.groupingBy(FlashSaleItem::getSessionId));

        return slots.stream().map(slot -> {
            Map<Long, List<AdminSessionView.Item>> bySeller = new LinkedHashMap<>();
            itemsBySession.getOrDefault(slot.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(FlashSaleItem::getSellerId).thenComparing(FlashSaleItem::getId))
                    .forEach(item -> {
                        Product product = productById.get(item.getProductId());
                        bySeller.computeIfAbsent(item.getSellerId(), id -> new ArrayList<>())
                                .add(new AdminSessionView.Item(item.getId(), item.getProductId(),
                                        product == null ? null : product.getSku(),
                                        product == null ? null : product.getName(), item.getRuleId(),
                                        item.getSalePrice(), item.getQuota(), item.getSold(), item.getStatus().name()));
                    });
            List<AdminSessionView.SellerGroup> groups = bySeller.entrySet().stream()
                    .map(e -> new AdminSessionView.SellerGroup(e.getKey(), e.getValue())).toList();
            return new AdminSessionView(slot.getId(), slot.getName(), slot.getStartAt(), slot.getEndAt(),
                    slot.getStatus().name(), slot.getCreatedBy() == null, groups);
        }).toList();
    }

    @Override
    public GenerationResult generateNow(String region) {
        requireConfig(region);
        return generator.generate(region);
    }

    private FlashSaleConfig requireConfig(String region) {
        return configs.findById(region).orElseThrow(() -> new ApiException(ErrorCode.FLASH_SALE_CONFIG_NOT_FOUND));
    }
}
