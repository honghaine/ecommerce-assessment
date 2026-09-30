package com.flashsale.inventory.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.inventory.entity.Inventory;
import com.flashsale.inventory.entity.InventoryMovement;
import com.flashsale.inventory.entity.MovementReason;
import com.flashsale.inventory.repository.InventoryMovementRepository;
import com.flashsale.inventory.repository.InventoryRepository;
import com.flashsale.inventory.service.InventoryService;

/** Every stock change is a conditional UPDATE plus a ledger row, in the caller's transaction. */
@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventories;
    private final InventoryMovementRepository movements;

    public InventoryServiceImpl(InventoryRepository inventories, InventoryMovementRepository movements) {
        this.inventories = inventories;
        this.movements = movements;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void open(long productId, String region, int quantity) {
        inventories.save(Inventory.open(productId, region, quantity));
        movements.save(InventoryMovement.of(productId, region, quantity, MovementReason.RESTOCK, null));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserve(long productId, String region, int quantity) {
        if (inventories.reserve(productId, quantity) == 0) {
            return false;
        }
        movements.save(InventoryMovement.of(productId, region, -quantity, MovementReason.RESERVE, null));
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean restock(long productId, String region, int quantity, String idempotencyKey) {
        if (movements.existsByProductIdAndIdempotencyKey(productId, idempotencyKey)) {
            return false;
        }
        inventories.restock(productId, quantity);
        // UNIQUE (product_id, idempotency_key) makes a concurrent duplicate fail the whole transaction.
        movements.saveAndFlush(InventoryMovement.withIdempotencyKey(productId, region, quantity,
                MovementReason.RESTOCK, idempotencyKey));
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleFlashSaleItem(long itemId, long productId, String region, int quota, int sold, UUID eventId) {
        int alreadyDeducted = inventories.countDeductedPerOrder(itemId);   // > 0 only for pre-change items
        int toDeduct = sold - alreadyDeducted;
        int unsold = quota - sold;
        if (inventories.settleFlashSale(productId, quota - alreadyDeducted, toDeduct, unsold) == 0) {
            // Should never happen: the quota was reserved before the slot. Fail → retried, then dead-lettered.
            throw new IllegalStateException("Reserved stock of product " + productId + " is lower than the quota");
        }
        // Ledger: one row carries the event id (UNIQUE ref_event_id); the effect itself is deduped by processed_events.
        UUID ref = eventId;
        if (toDeduct > 0) {
            movements.save(InventoryMovement.of(productId, region, -toDeduct, MovementReason.PURCHASE, ref));
            ref = null;
        }
        if (unsold > 0 || ref != null) {
            movements.save(InventoryMovement.of(productId, region, unsold, MovementReason.RELEASE, ref));
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean applyWarehouseDelta(long productId, String region, int delta) {
        if (inventories.adjustAvailable(productId, delta) == 0) {
            return false;
        }
        movements.save(InventoryMovement.of(productId, region, delta, MovementReason.WAREHOUSE_SYNC, null));
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long productId, String region, int quantity) {
        if (quantity > 0 && inventories.release(productId, quantity) > 0) {
            movements.save(InventoryMovement.of(productId, region, quantity, MovementReason.RELEASE, null));
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(long productId, String region, int quantity) {
        inventories.restock(productId, quantity);
        movements.save(InventoryMovement.of(productId, region, quantity, MovementReason.RESTOCK, null));
    }
}
