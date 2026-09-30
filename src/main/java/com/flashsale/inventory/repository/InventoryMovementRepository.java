package com.flashsale.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.inventory.entity.InventoryMovement;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    boolean existsByProductIdAndIdempotencyKey(long productId, String idempotencyKey);
}
