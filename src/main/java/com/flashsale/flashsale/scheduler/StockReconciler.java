package com.flashsale.flashsale.scheduler;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.flashsale.config.FlashSaleProperties;
import com.flashsale.flashsale.model.ItemSnapshot;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.support.StockGate;

/**
 * Rebuilds Redis stock counters from the database ({@code quota - sold}) for live and upcoming slots:
 * pre-warms counters before a slot opens and heals drift (e.g. an instance died between the Redis
 * gate and the DB commit). Runs on one instance at a time (ShedLock).
 */
@Slf4j
@Component
public class StockReconciler {

    private final FlashSaleItemRepository items;
    private final StockGate stockGate;
    private final FlashSaleProperties properties;

    public StockReconciler(FlashSaleItemRepository items, StockGate stockGate, FlashSaleProperties properties) {
        this.items = items;
        this.stockGate = stockGate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.flash-sale.reconcile-interval-ms:15000}",
            initialDelayString = "${app.flash-sale.reconcile-initial-delay-ms:5000}")
    @SchedulerLock(name = "flashSaleStockReconciler", lockAtMostFor = "PT1M")
    @Transactional(readOnly = true)
    public void reconcile() {
        Instant now = Instant.now();
        try {
            int count = 0;
            for (ItemSnapshot item : items.findLiveOrUpcoming(now, now.plus(properties.reconcileHorizon()))) {
                stockGate.resetStock(item);
                count++;
            }
            log.debug("Reconciled {} flash sale stock counters", count);
        } catch (DataAccessException ex) {
            log.warn("Stock reconciliation skipped: {}", ex.getMessage());
        }
    }
}
