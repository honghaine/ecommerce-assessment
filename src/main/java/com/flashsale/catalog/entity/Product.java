package com.flashsale.catalog.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Product owned by a seller; its region is the seller's region (composite FK). */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Product create(String region, long sellerId, String sku, String name, String description,
                                 BigDecimal price) {
        Product product = new Product();
        product.region = region;
        product.sellerId = sellerId;
        product.sku = sku;
        product.name = name;
        product.description = description;
        product.price = price;
        product.status = ProductStatus.ACTIVE;
        product.createdAt = Instant.now();
        product.updatedAt = product.createdAt;
        return product;
    }

    /** Applies non-null fields. */
    public void update(String name, String description, BigDecimal price, ProductStatus status) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            this.price = price;
        }
        if (status != null) {
            this.status = status;
        }
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
