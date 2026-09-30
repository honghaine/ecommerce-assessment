package com.flashsale.inventory.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.catalog.entity.Product;
import com.flashsale.catalog.repository.ProductRepository;
import com.flashsale.common.metrics.BusinessMetrics;
import com.flashsale.inventory.dto.WarehouseSyncRequest;
import com.flashsale.inventory.dto.WarehouseSyncRequest.StockEvent;
import com.flashsale.inventory.dto.WarehouseSyncResponse;
import com.flashsale.inventory.entity.InventorySyncLog;
import com.flashsale.inventory.repository.InventorySyncLogRepository;
import com.flashsale.inventory.service.InventoryService;
import com.flashsale.inventory.service.WarehouseSyncService;

/**
 * Each event in its own transaction: [log row (UNIQUE source+eventId) + stock delta + ledger row] commit together,
 * so a re-sent or concurrently duplicated event can never be applied twice, and one bad event does not block
 * the rest of the batch.
 */
@Slf4j
@Service
public class WarehouseSyncServiceImpl implements WarehouseSyncService {

    private static final String APPLIED = "APPLIED", REJECTED = "REJECTED", DUPLICATE = "DUPLICATE";

    private final InventorySyncLogRepository syncLog;
    private final ProductRepository products;
    private final InventoryService inventoryService;
    private final TransactionTemplate transactionTemplate;
    private final BusinessMetrics metrics;

    public WarehouseSyncServiceImpl(InventorySyncLogRepository syncLog, ProductRepository products,
                                    InventoryService inventoryService, TransactionTemplate transactionTemplate,
                                    BusinessMetrics metrics) {
        this.syncLog = syncLog;
        this.products = products;
        this.inventoryService = inventoryService;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    @Override
    public WarehouseSyncResponse apply(WarehouseSyncRequest request) {
        List<WarehouseSyncResponse.Result> results = new ArrayList<>();
        for (StockEvent event : request.events()) {
            WarehouseSyncResponse.Result result = applyOne(request.source(), event);
            metrics.warehouseEvent(result.status());
            results.add(result);
        }
        return new WarehouseSyncResponse(results);
    }

    private WarehouseSyncResponse.Result applyOne(String source, StockEvent event) {
        Optional<InventorySyncLog> seen = syncLog.findBySourceAndExternalEventId(source, event.eventId());
        if (seen.isPresent()) {
            return duplicate(seen.get());
        }
        try {
            return transactionTemplate.execute(status -> {
                String region = event.region().trim().toUpperCase(java.util.Locale.ROOT);
                Optional<Product> product = products.findByRegionAndSku(region, event.sku().trim().toUpperCase(java.util.Locale.ROOT));
                String outcome;
                String detail;
                if (product.isEmpty()) {
                    outcome = REJECTED;
                    detail = "UNKNOWN_SKU";
                } else if (inventoryService.applyWarehouseDelta(product.get().getId(), region, event.delta())) {
                    outcome = APPLIED;
                    detail = null;
                } else {
                    outcome = REJECTED;
                    detail = "WOULD_MAKE_AVAILABLE_NEGATIVE";
                }
                syncLog.saveAndFlush(InventorySyncLog.of(source, event.eventId(), region, event.sku(),
                        product.map(Product::getId).orElse(null), event.delta(), event.reason(), outcome, detail));
                return new WarehouseSyncResponse.Result(event.eventId(), outcome, detail);
            });
        } catch (DataIntegrityViolationException race) {
            // Same event delivered concurrently: the other request won; report its outcome.
            return syncLog.findBySourceAndExternalEventId(source, event.eventId())
                    .map(this::duplicate)
                    .orElseThrow(() -> race);
        }
    }

    private WarehouseSyncResponse.Result duplicate(InventorySyncLog original) {
        String detail = "original: " + original.getStatus()
                + (original.getDetail() == null ? "" : " (" + original.getDetail() + ")");
        return new WarehouseSyncResponse.Result(original.getExternalEventId(), DUPLICATE, detail);
    }
}
