package com.flashsale.inventory.service.impl;

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
    public void restock(long productId, String region, int quantity) {
        inventories.restock(productId, quantity);
        movements.save(InventoryMovement.of(productId, region, quantity, MovementReason.RESTOCK, null));
    }
}
