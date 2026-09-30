package com.flashsale.catalog.service;

import java.util.List;

import com.flashsale.catalog.dto.CreateProductRequest;
import com.flashsale.catalog.dto.ProductResponse;
import com.flashsale.catalog.dto.UpdateProductRequest;
import com.flashsale.catalog.entity.Product;

/** Seller-side product management. Every call is scoped to the calling seller's own products. */
public interface ProductService {

    ProductResponse create(long sellerId, String region, CreateProductRequest request);

    List<ProductResponse> listOwn(long sellerId);

    /** Applied at most once per {@code idempotencyKey}; a retried call returns the current stock unchanged. */
    ProductResponse restock(long sellerId, long productId, int quantity, String idempotencyKey);

    /**
     * Edits name/description/price/status. A price or status change publishes {@code PRODUCT_CHANGED} so flash
     * sales and stock follow (rules that no longer fit are paused, their future occurrences' stock returned).
     */
    ProductResponse update(long sellerId, long productId, UpdateProductRequest request);

    /** @throws com.flashsale.common.error.ApiException PRODUCT_NOT_FOUND if missing or not owned */
    Product requireOwn(long sellerId, long productId);
}
