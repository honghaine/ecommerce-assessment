package com.flashsale.flashsale.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.metrics.BusinessMetrics;
import com.flashsale.common.ratelimit.RateLimiter;
import com.flashsale.flashsale.entity.UserDailyPurchase;
import com.flashsale.flashsale.model.ItemSnapshot;
import com.flashsale.flashsale.model.PurchaseResult;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.service.PurchaseService;
import com.flashsale.flashsale.support.StockGate;
import com.flashsale.flashsale.support.StockGate.Compensation;
import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.region.service.RegionService;
import com.flashsale.user.repository.WalletRepository;

/**
 * Purchase orchestration: rate limit → idempotency → cheap pre-checks → Redis gate → DB transaction
 * → compensate the gate if the DB said no. Only the DB transaction decides success.
 */
@Slf4j
@Service
public class PurchaseServiceImpl implements PurchaseService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final RateLimiter rateLimiter;
    private final OrderRepository orders;
    private final FlashSaleItemRepository items;
    private final WalletRepository wallets;
    private final StockGate stockGate;
    private final PurchaseTransaction purchaseTransaction;
    private final RegionService regionService;
    private final BusinessMetrics metrics;

    public PurchaseServiceImpl(RateLimiter rateLimiter, OrderRepository orders, FlashSaleItemRepository items,
                               WalletRepository wallets, StockGate stockGate,
                               PurchaseTransaction purchaseTransaction, RegionService regionService,
                               BusinessMetrics metrics) {
        this.rateLimiter = rateLimiter;
        this.orders = orders;
        this.items = items;
        this.wallets = wallets;
        this.stockGate = stockGate;
        this.purchaseTransaction = purchaseTransaction;
        this.regionService = regionService;
        this.metrics = metrics;
    }

    /** Records outcome + latency of every attempt (success / replay / business error code / error). */
    @Override
    public PurchaseResult purchase(long userId, String userRegion, long itemId, String idempotencyKey) {
        long start = System.nanoTime();
        String result = "error";
        try {
            PurchaseResult purchase = doPurchase(userId, userRegion, itemId, idempotencyKey);
            result = purchase.replayed() ? "replay" : "success";
            return purchase;
        } catch (ApiException ex) {
            result = ex.errorCode().name();
            throw ex;
        } finally {
            metrics.purchase(result, Duration.ofNanos(System.nanoTime() - start));
        }
    }

    private PurchaseResult doPurchase(long userId, String userRegion, long itemId, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        rateLimiter.check("purchase-user", String.valueOf(userId));

        Optional<PurchaseResult> replay = replay(userId, itemId, idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }

        // Cheap pre-checks; other markets' items look non-existent.
        ItemSnapshot item = items.findSnapshot(itemId)
                .filter(snapshot -> snapshot.region().equals(userRegion))
                .orElseThrow(() -> new ApiException(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND));
        if (!item.isLiveAt(Instant.now())) {
            throw new ApiException(ErrorCode.FLASH_SALE_NOT_ACTIVE);
        }

        StockGate.Result gate = stockGate.tryAcquire(item, userId, regionService.timezone(userRegion));
        switch (gate) {
            case SOLD_OUT -> throw new ApiException(ErrorCode.SOLD_OUT);
            case ALREADY_PURCHASED -> {
                // A concurrent retry with the same key may have just committed.
                return replay(userId, itemId, idempotencyKey)
                        .orElseThrow(() -> new ApiException(ErrorCode.ALREADY_PURCHASED_TODAY));
            }
            case PASSED, BYPASSED -> {
                // continue to the database, which has the final say
            }
        }

        try {
            PurchaseResult result = purchaseTransaction.execute(item, userId, userRegion, idempotencyKey);
            log.info("Flash sale purchase: order={} user={} item={}", result.order().getId(), userId, itemId);
            return result;
        } catch (ApiException ex) {
            compensate(gate, item, userId, ex.errorCode() == ErrorCode.SOLD_OUT
                    ? Compensation.SOLD_OUT : Compensation.RELEASE);
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            String constraint = constraintName(ex);
            if (Order.IDEMPOTENCY_CONSTRAINT.equals(constraint)) {
                compensate(gate, item, userId, Compensation.ALREADY_PURCHASED);
                return replay(userId, itemId, idempotencyKey)
                        .orElseThrow(() -> new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED));
            }
            if (UserDailyPurchase.PK_CONSTRAINT.equals(constraint)) {
                compensate(gate, item, userId, Compensation.ALREADY_PURCHASED);
                throw new ApiException(ErrorCode.ALREADY_PURCHASED_TODAY);
            }
            compensate(gate, item, userId, Compensation.RELEASE);
            throw ex;
        } catch (RuntimeException ex) {
            compensate(gate, item, userId, Compensation.RELEASE);
            throw ex;
        }
    }

    /** Same key + same item → original order; same key + other item → conflict. */
    private Optional<PurchaseResult> replay(long userId, long itemId, String idempotencyKey) {
        return orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(order -> {
            if (order.getFlashSaleItemId() != itemId) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new PurchaseResult(order, wallets.findBalance(userId), true);
        });
    }

    private void compensate(StockGate.Result gate, ItemSnapshot item, long userId, Compensation compensation) {
        if (gate == StockGate.Result.PASSED) {
            stockGate.compensate(item, userId, compensation);
        }
    }

    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
