package com.flashsale.catalog.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.catalog.dto.CreateProductRequest;
import com.flashsale.catalog.dto.ProductResponse;
import com.flashsale.catalog.dto.UpdateProductRequest;
import com.flashsale.catalog.entity.ProductStatus;
import com.flashsale.catalog.entity.Product;
import com.flashsale.catalog.repository.ProductRepository;
import com.flashsale.catalog.service.ProductService;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.inventory.entity.Inventory;
import com.flashsale.inventory.repository.InventoryRepository;
import com.flashsale.inventory.service.InventoryService;
import com.flashsale.outbox.service.DomainEventPublisher;
import com.flashsale.region.service.RegionService;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final InventoryService inventoryService;
    private final RegionService regionService;
    private final TransactionTemplate transactionTemplate;
    private final DomainEventPublisher events;

    public ProductServiceImpl(ProductRepository products, InventoryRepository inventories,
                              InventoryService inventoryService, RegionService regionService,
                              TransactionTemplate transactionTemplate, DomainEventPublisher events) {
        this.products = products;
        this.inventories = inventories;
        this.inventoryService = inventoryService;
        this.regionService = regionService;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
    }

    @Override
    public ProductResponse create(long sellerId, String region, CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (products.existsByRegionAndSku(region, sku)) {
            throw new ApiException(ErrorCode.SKU_ALREADY_EXISTS);
        }
        try {
            return transactionTemplate.execute(status -> {
                Product product = products.saveAndFlush(Product.create(region, sellerId, sku, request.name().trim(),
                        request.description(), request.price().setScale(2, RoundingMode.HALF_UP)));
                inventoryService.open(product.getId(), region, request.stock());
                return toResponse(product, Inventory.open(product.getId(), region, request.stock()));
            });
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(ErrorCode.SKU_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> listOwn(long sellerId) {
        List<Product> own = products.findBySellerIdOrderByIdDesc(sellerId);
        Map<Long, Inventory> stock = inventories.findAllById(own.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        return own.stream().map(product -> toResponse(product, stock.get(product.getId()))).toList();
    }

    @Override
    public ProductResponse restock(long sellerId, long productId, int quantity, String idempotencyKey) {
        Product product = requireOwn(sellerId, productId);
        try {
            transactionTemplate.executeWithoutResult(status ->
                    inventoryService.restock(productId, product.getRegion(), quantity, idempotencyKey));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // same key applied by a concurrent request — nothing more to do
        }
        return toResponse(product, inventories.findById(productId).orElse(null));
    }

    @Override
    @Transactional
    public ProductResponse update(long sellerId, long productId, UpdateProductRequest request) {
        Product product = requireOwn(sellerId, productId);
        BigDecimal newPrice = request.price() == null ? null : request.price().setScale(2, RoundingMode.HALF_UP);
        boolean priceChanged = newPrice != null && newPrice.compareTo(product.getPrice()) != 0;
        boolean statusChanged = request.status() != null && request.status() != product.getStatus();
        product.update(request.name() == null ? null : request.name().trim(), request.description(), newPrice,
                request.status());
        products.saveAndFlush(product);
        if (priceChanged || statusChanged) {
            events.publish(product.getRegion(), "PRODUCT", String.valueOf(productId), "PRODUCT_CHANGED", Map.of(
                    "productId", productId,
                    "status", product.getStatus().name(),
                    "price", product.getPrice()));
        }
        return toResponse(product, inventories.findById(productId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public Product requireOwn(long sellerId, long productId) {
        return products.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductResponse toResponse(Product product, Inventory inventory) {
        return new ProductResponse(product.getId(), product.getRegion(), product.getSku(), product.getName(),
                product.getDescription(), product.getPrice(),
                regionService.currency(product.getRegion()).getCurrencyCode(), product.getStatus().name(),
                inventory == null ? 0 : inventory.getTotal(), inventory == null ? 0 : inventory.getAvailable(),
                inventory == null ? 0 : inventory.getReserved());
    }
}
