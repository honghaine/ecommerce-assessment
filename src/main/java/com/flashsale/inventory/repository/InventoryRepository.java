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

    /**
     * Flash-sale slot closed: the item's remaining reservation leaves {@code reserved}; sold units leave {@code total};
     * unsold units return to {@code available}. One statement keeps {@code available + reserved = total}.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET reserved = reserved - :releaseReserved, total = total - :sold, available = available + :unsold,
                version = version + 1, updated_at = now()
            WHERE product_id = :productId AND reserved >= :releaseReserved
            """, nativeQuery = true)
    int settleFlashSale(long productId, int releaseReserved, int sold, int unsold);

    /**
     * Cutover only: units of this item already deducted per order by the previous model
     * (PURCHASE ledger rows keyed by ORDER_CREATED events of the item). Zero for items sold after the change.
     */
    @Query(value = """
            SELECT COALESCE(-SUM(m.delta), 0) FROM inventory_movements m
            JOIN outbox_events e ON e.id = m.ref_event_id
            WHERE m.reason = 'PURCHASE' AND e.event_type = 'ORDER_CREATED'
              AND (e.payload ->> 'flashSaleItemId')::bigint = :itemId
            """, nativeQuery = true)
    int countDeductedPerOrder(long itemId);

    /** Warehouse correction/receipt on sellable stock; never lets available go negative. */
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET total = total + :delta, available = available + :delta,
                version = version + 1, updated_at = now()
            WHERE product_id = :productId AND available + :delta >= 0
            """, nativeQuery = true)
    int adjustAvailable(long productId, int delta);

    /** Returns unsold flash-sale quota to available stock. */
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET available = available + :quantity, reserved = reserved - :quantity,
                version = version + 1, updated_at = now()
            WHERE product_id = :productId AND reserved >= :quantity
            """, nativeQuery = true)
    int release(long productId, int quantity);

    @Modifying
    @Query(value = """
            UPDATE inventory
            SET total = total + :quantity, available = available + :quantity,
                version = version + 1, updated_at = now()
            WHERE product_id = :productId
            """, nativeQuery = true)
    int restock(long productId, int quantity);
}
