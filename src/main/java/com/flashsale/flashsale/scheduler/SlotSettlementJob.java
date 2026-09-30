package com.flashsale.flashsale.scheduler;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.common.metrics.BusinessMetrics;
import com.flashsale.flashsale.repository.FlashSaleSettlementDao;
import com.flashsale.outbox.service.DomainEventPublisher;

/**
 * Flash-sale stock is locked before the slot (quota reserved) and not touched while it runs; it is reconciled once
 * when the slot ends. Each still-reserved item publishes {@code FLASH_SALE_ITEM_CLOSED {quota, sold}} in the same
 * transaction that marks it settled → inventory removes the sold units and returns the unsold ones, exactly once.
 * Withdrawn / rejected items already had their stock returned, so they are only marked settled.
 */
@Slf4j
@Component
public class SlotSettlementJob {

    private static final int BATCH = 200;

    private final FlashSaleSettlementDao settlementDao;
    private final DomainEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final BusinessMetrics metrics;

    public SlotSettlementJob(FlashSaleSettlementDao settlementDao, DomainEventPublisher events,
                             TransactionTemplate transactionTemplate, BusinessMetrics metrics) {
        this.settlementDao = settlementDao;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.flash-sale.settle-interval-ms:30000}", initialDelayString = "15000")
    public void settleEndedSlots() {
        try {
            Integer closed = transactionTemplate.execute(status -> {
                int count = 0;
                for (Long itemId : settlementDao.findEndedUnsettled(BATCH)) {
                    var settled = settlementDao.settle(itemId);
                    if (settled.isEmpty()) {
                        continue;
                    }
                    var item = settled.get();
                    if ("APPROVED".equals(item.status()) || "PENDING".equals(item.status())) {
                        events.publish(item.region(), "FLASH_SALE_ITEM", String.valueOf(item.itemId()),
                                "FLASH_SALE_ITEM_CLOSED", Map.of("itemId", item.itemId(),
                                        "productId", item.productId(), "quota", item.quota(), "sold", item.sold(),
                                        "unsold", item.quota() - item.sold()));
                    }
                    count++;
                }
                return count;
            });
            if (closed != null && closed > 0) {
                metrics.settled(closed);
                log.info("Settled {} ended flash-sale items", closed);
            }
        } catch (RuntimeException ex) {
            log.warn("Slot settlement failed (will retry): {}", ex.getMessage());
        }
    }
}
