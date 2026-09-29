package com.flashsale.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.flashsale.inventory.entity.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** Moves stock from available to reserved; 0 rows = not enough available stock. */
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET available = available - :quantity, reserved = reserved + :quantity,
                version = version + 1, updated_at = now()
            WHERE product_id = :productId AND available >= :quantity
            """, nativeQuery = true)
    int reserve(long productId, int quantity);

    @Modifying
    @Query(value = """
            UPDATE inventory
            SET total = total + :quantity, available = available + :quantity,
                version = version + 1, updated_at = now()
            WHERE product_id = :productId
            """, nativeQuery = true)
    int restock(long productId, int quantity);
}
