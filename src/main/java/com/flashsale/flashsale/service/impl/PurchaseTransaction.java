package com.flashsale.flashsale.service.impl;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.flashsale.entity.UserDailyPurchase;
import com.flashsale.flashsale.model.ItemSnapshot;
import com.flashsale.flashsale.model.PurchaseResult;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.repository.FlashSaleItemStockDao;
import com.flashsale.flashsale.repository.FlashSaleItemStockDao.ClaimedUnit;
import com.flashsale.flashsale.repository.UserDailyPurchaseRepository;
import com.flashsale.order.entity.Order;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.outbox.service.DomainEventPublisher;
import com.flashsale.user.entity.WalletTransaction;
import com.flashsale.user.repository.WalletRepository;
import com.flashsale.user.repository.WalletTransactionRepository;

/**
 * The single database transaction that makes a purchase. Every rule is enforced by the database,
 * so it stays correct across any number of instances and concurrent requests:
 * <ol>
 *   <li>claim one unit — atomic {@code UPDATE ... WHERE sold < quota AND now() in window};</li>
 *   <li>debit wallet — atomic {@code UPDATE ... WHERE balance >= amount};</li>
 *   <li>insert order — {@code UNIQUE (user_id, idempotency_key)};</li>
 *   <li>insert daily purchase — PK {@code (user_id, purchase_date)} = 1 product/user/day;</li>
 *   <li>ledger row + {@code ORDER_CREATED} outbox event (for fulfilment/notifications — inventory is not touched
 *       during the slot; it is settled once when the slot ends).</li>
 * </ol>
 * Lock order is always item row → wallet row, so concurrent purchases cannot deadlock.
 */
@Component
public class PurchaseTransaction {

    private final FlashSaleItemStockDao stockDao;
    private final FlashSaleItemRepository items;
    private final WalletRepository wallets;
    private final WalletTransactionRepository walletTransactions;
    private final OrderRepository orders;
    private final UserDailyPurchaseRepository dailyPurchases;
    private final DomainEventPublisher events;

    public PurchaseTransaction(FlashSaleItemStockDao stockDao, FlashSaleItemRepository items,
                               WalletRepository wallets, WalletTransactionRepository walletTransactions,
                               OrderRepository orders, UserDailyPurchaseRepository dailyPurchases,
                               DomainEventPublisher events) {
        this.stockDao = stockDao;
        this.items = items;
        this.wallets = wallets;
        this.walletTransactions = walletTransactions;
        this.orders = orders;
        this.dailyPurchases = dailyPurchases;
        this.events = events;
    }

    @Transactional
    public PurchaseResult execute(ItemSnapshot item, long userId, String region, String idempotencyKey) {
        ClaimedUnit unit = stockDao.claimOne(item.itemId(), region)
                .orElseThrow(() -> new ApiException(whyNotClaimed(item.itemId())));

        if (wallets.debit(userId, unit.salePrice()) == 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        Order order = orders.saveAndFlush(Order.paidFlashSale(region, userId, item.itemId(), unit.productId(),
                unit.salePrice(), idempotencyKey));
        dailyPurchases.saveAndFlush(UserDailyPurchase.of(userId, unit.saleDate(), region, order.getId()));
        walletTransactions.save(WalletTransaction.debitForOrder(userId, region, order.getId(), unit.salePrice()));
        events.publish(region, "ORDER", String.valueOf(order.getId()), "ORDER_CREATED", Map.of(
                "orderId", order.getId(),
                "userId", userId,
                "flashSaleItemId", item.itemId(),
                "productId", unit.productId(),
                "quantity", 1,
                "amount", unit.salePrice()));

        return new PurchaseResult(order, wallets.findBalance(userId), false);
    }

    /** Claim failed: re-read the (committed) row to tell sold-out from not-live. */
    private ErrorCode whyNotClaimed(long itemId) {
        return items.findSnapshot(itemId)
                .map(snapshot -> snapshot.remaining() == 0 ? ErrorCode.SOLD_OUT : ErrorCode.FLASH_SALE_NOT_ACTIVE)
                .orElse(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND);
    }
}
