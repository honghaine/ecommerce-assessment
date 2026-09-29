package com.flashsale.order.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Flash-sale order. {@code UNIQUE (user_id, idempotency_key)} makes retries safe. */
@Entity(name = "PurchaseOrder")
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    public static final String IDEMPOTENCY_CONSTRAINT = "uq_orders_idempotency";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "flash_sale_item_id", nullable = false, updatable = false)
    private Long flashSaleItemId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Balance is debited in the same transaction, so the order is created already PAID. */
    public static Order paidFlashSale(String region, long userId, long itemId, long productId, BigDecimal amount,
                                      String idempotencyKey) {
        Order order = new Order();
        order.region = region;
        order.userId = userId;
        order.flashSaleItemId = itemId;
        order.productId = productId;
        order.amount = amount;
        order.quantity = 1;
        order.status = OrderStatus.PAID;
        order.idempotencyKey = idempotencyKey;
        order.createdAt = Instant.now();
        return order;
    }
}
