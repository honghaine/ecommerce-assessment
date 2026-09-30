package com.flashsale.flashsale.support;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.flashsale.entity.FlashSaleItem;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.inventory.service.InventoryService;

/**
 * Removes a rule's not-yet-started occurrences and returns their reserved stock (no orders can exist before a slot
 * starts). Occurrences the seller withdrew one by one stay WITHDRAWN so the generator will not recreate them.
 */
@Component
public class RuleOccurrenceCleaner {

    private final FlashSaleItemRepository items;
    private final InventoryService inventoryService;

    public RuleOccurrenceCleaner(FlashSaleItemRepository items, InventoryService inventoryService) {
        this.items = items;
        this.inventoryService = inventoryService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int removeFutureItems(long ruleId) {
        int removed = 0;
        for (FlashSaleItem item : items.findFutureActiveByRule(ruleId, Instant.now())) {
            inventoryService.release(item.getProductId(), item.getRegion(), item.getQuota() - item.getSold());
            items.delete(item);
            removed++;
        }
        items.flush();
        return removed;
    }
}
