package com.flashsale.flashsale.service.impl;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.catalog.entity.Product;
import com.flashsale.catalog.entity.ProductStatus;
import com.flashsale.catalog.service.ProductService;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.flashsale.dto.RuleRequest;
import com.flashsale.flashsale.dto.RuleResponse;
import com.flashsale.flashsale.dto.SellerItemResponse;
import com.flashsale.flashsale.entity.FlashSaleConfig;
import com.flashsale.flashsale.entity.FlashSaleItem;
import com.flashsale.flashsale.entity.FlashSaleSession;
import com.flashsale.flashsale.entity.RuleStatus;
import com.flashsale.flashsale.entity.SellerFlashSaleRule;
import com.flashsale.flashsale.repository.FlashSaleConfigRepository;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.repository.FlashSaleSessionRepository;
import com.flashsale.flashsale.repository.SellerFlashSaleRuleRepository;
import com.flashsale.flashsale.service.SellerFlashSaleService;
import com.flashsale.flashsale.service.SlotGenerationService;
import com.flashsale.flashsale.support.DaysOfWeek;
import com.flashsale.flashsale.support.RuleOccurrenceCleaner;
import com.flashsale.inventory.service.InventoryService;
import com.flashsale.region.service.RegionService;

@Service
public class SellerFlashSaleServiceImpl implements SellerFlashSaleService {

    private final SellerFlashSaleRuleRepository rules;
    private final FlashSaleItemRepository items;
    private final FlashSaleSessionRepository sessions;
    private final FlashSaleConfigRepository configs;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final SlotGenerationService generator;
    private final RegionService regionService;
    private final TransactionTemplate transactionTemplate;
    private final RuleOccurrenceCleaner occurrenceCleaner;

    public SellerFlashSaleServiceImpl(SellerFlashSaleRuleRepository rules, FlashSaleItemRepository items,
                                      FlashSaleSessionRepository sessions, FlashSaleConfigRepository configs,
                                      ProductService productService, InventoryService inventoryService,
                                      SlotGenerationService generator, RegionService regionService,
                                      TransactionTemplate transactionTemplate,
                                      RuleOccurrenceCleaner occurrenceCleaner) {
        this.rules = rules;
        this.items = items;
        this.sessions = sessions;
        this.configs = configs;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.generator = generator;
        this.regionService = regionService;
        this.transactionTemplate = transactionTemplate;
        this.occurrenceCleaner = occurrenceCleaner;
    }

    @Override
    public RuleResponse createRule(long sellerId, String region, RuleRequest request) {
        Product product = validate(sellerId, region, request);
        if (rules.existsByProductIdAndSlotStartTimeAndStatusNot(product.getId(), request.slotStartTime(),
                RuleStatus.ARCHIVED)) {
            throw new ApiException(ErrorCode.RULE_ALREADY_EXISTS);
        }
        SellerFlashSaleRule rule;
        try {
            rule = rules.saveAndFlush(SellerFlashSaleRule.create(region, sellerId, product.getId(),
                    request.slotStartTime(), DaysOfWeek.toMask(request.daysOfWeek()),
                    request.salePrice().setScale(2, RoundingMode.HALF_UP), request.quota()));
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(ErrorCode.RULE_ALREADY_EXISTS);
        }
        generator.generate(region);
        return RuleResponse.from(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleResponse> listRules(long sellerId) {
        return rules.findBySellerIdAndStatusNotOrderBySlotStartTimeAscIdAsc(sellerId, RuleStatus.ARCHIVED).stream()
                .map(RuleResponse::from).toList();
    }

    @Override
    public RuleResponse updateRule(long sellerId, long ruleId, RuleRequest request) {
        SellerFlashSaleRule current = requireRule(sellerId, ruleId);
        if (!current.getProductId().equals(request.productId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);   // a rule is bound to one product
        }
        validate(sellerId, current.getRegion(), request);
        if (rules.existsByProductIdAndSlotStartTimeAndStatusNotAndIdNot(current.getProductId(),
                request.slotStartTime(), RuleStatus.ARCHIVED, ruleId)) {
            throw new ApiException(ErrorCode.RULE_ALREADY_EXISTS);
        }
        RuleResponse response = transactionTemplate.execute(status -> {
            SellerFlashSaleRule rule = requireRule(sellerId, ruleId);
            occurrenceCleaner.removeFutureItems(rule.getId());
            rule.update(request.slotStartTime(), DaysOfWeek.toMask(request.daysOfWeek()),
                    request.salePrice().setScale(2, RoundingMode.HALF_UP), request.quota());
            return RuleResponse.from(rules.saveAndFlush(rule));
        });
        generator.generate(current.getRegion());
        return response;
    }

    @Override
    public RuleResponse changeStatus(long sellerId, long ruleId, RuleStatus target) {
        RuleResponse response = transactionTemplate.execute(status -> {
            SellerFlashSaleRule rule = requireRule(sellerId, ruleId);
            if (rule.getStatus() == RuleStatus.ARCHIVED) {
                throw new ApiException(ErrorCode.RULE_ARCHIVED);
            }
            if (target != RuleStatus.ACTIVE) {
                occurrenceCleaner.removeFutureItems(rule.getId());
            }
            rule.changeStatus(target);
            return RuleResponse.from(rules.saveAndFlush(rule));
        });
        if (target == RuleStatus.ACTIVE) {
            generator.generate(requireRule(sellerId, ruleId).getRegion());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerItemResponse> listItems(long sellerId, String region, LocalDate date) {
        ZoneId zone = regionService.timezone(region);
        LocalDate day = date != null ? date : LocalDate.now(zone);
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return items.findSellerItems(sellerId, from, to).stream()
                .map(row -> toResponse((FlashSaleItem) row[0], (FlashSaleSession) row[1]))
                .toList();
    }

    @Override
    @Transactional
    public SellerItemResponse withdrawItem(long sellerId, long itemId) {
        FlashSaleItem item = items.findByIdAndSellerId(itemId, sellerId)
                .orElseThrow(() -> new ApiException(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND));
        FlashSaleSession session = sessions.findById(item.getSessionId())
                .orElseThrow(() -> new ApiException(ErrorCode.FLASH_SALE_SESSION_NOT_FOUND));
        if (!session.getStartAt().isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.SLOT_ALREADY_STARTED);
        }
        if (!item.isWithdrawable()) {
            throw new ApiException(ErrorCode.ITEM_NOT_WITHDRAWABLE);
        }
        withdraw(item);
        return toResponse(item, session);
    }

    /** Ownership, product state, price, and alignment with the region's window grid. */
    private Product validate(long sellerId, String region, RuleRequest request) {
        Product product = productService.requireOwn(sellerId, request.productId());
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ApiException(ErrorCode.PRODUCT_INACTIVE);
        }
        if (request.salePrice().compareTo(product.getPrice()) >= 0) {
            throw new ApiException(ErrorCode.INVALID_SALE_PRICE);
        }
        if (request.daysOfWeek().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_DAYS_OF_WEEK);
        }
        FlashSaleConfig config = configs.findById(region)
                .orElseThrow(() -> new ApiException(ErrorCode.FLASH_SALE_CONFIG_NOT_FOUND));
        if (!config.isWindowStart(request.slotStartTime())) {
            throw new ApiException(ErrorCode.SLOT_TIME_NOT_ALIGNED);
        }
        return product;
    }

    private void withdraw(FlashSaleItem item) {
        item.withdraw();
        items.save(item);
        inventoryService.release(item.getProductId(), item.getRegion(), item.getQuota() - item.getSold());
    }

    private SellerFlashSaleRule requireRule(long sellerId, long ruleId) {
        return rules.findByIdAndSellerId(ruleId, sellerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RULE_NOT_FOUND));
    }

    private static SellerItemResponse toResponse(FlashSaleItem item, FlashSaleSession session) {
        return new SellerItemResponse(item.getId(), session.getId(), session.getName(), session.getStartAt(),
                session.getEndAt(), item.getProductId(), item.getRuleId(), item.getSalePrice(), item.getQuota(),
                item.getSold(), item.getStatus().name());
    }
}
