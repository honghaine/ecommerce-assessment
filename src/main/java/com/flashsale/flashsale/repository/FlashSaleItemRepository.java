package com.flashsale.flashsale.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.flashsale.flashsale.entity.FlashSaleItem;
import com.flashsale.flashsale.model.CurrentItemRow;
import com.flashsale.flashsale.model.ItemSnapshot;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    @Query("""
            select new com.flashsale.flashsale.model.ItemSnapshot(
                i.id, i.region, i.status, i.salePrice, i.productId, i.quota, i.sold,
                s.id, s.saleDate, s.startAt, s.endAt, s.status)
            from FlashSaleItem i join FlashSaleSession s on s.id = i.sessionId
            where i.id = :itemId
            """)
    Optional<ItemSnapshot> findSnapshot(long itemId);

    /** Approved items of every non-cancelled slot of the region that is live at {@code now}. */
    @Query("""
            select new com.flashsale.flashsale.model.CurrentItemRow(
                s.id, s.name, s.startAt, s.endAt,
                i.id, p.id, p.sku, p.name, p.price, i.salePrice, i.quota, i.sold)
            from FlashSaleItem i
                join FlashSaleSession s on s.id = i.sessionId
                join Product p on p.id = i.productId
            where s.region = :region
              and s.status <> com.flashsale.flashsale.entity.SessionStatus.CANCELLED
              and s.startAt <= :now and s.endAt > :now
              and i.status = com.flashsale.flashsale.entity.ItemStatus.APPROVED
              and p.status = com.flashsale.catalog.entity.ProductStatus.ACTIVE
            order by s.startAt, s.id, i.id
            """)
    List<CurrentItemRow> findLive(String region, Instant now);

    /** Items of slots that are live or start soon — used to (re)build Redis stock counters. */
    @Query("""
            select new com.flashsale.flashsale.model.ItemSnapshot(
                i.id, i.region, i.status, i.salePrice, i.productId, i.quota, i.sold,
                s.id, s.saleDate, s.startAt, s.endAt, s.status)
            from FlashSaleItem i join FlashSaleSession s on s.id = i.sessionId
            where s.endAt > :now and s.startAt < :horizon
              and s.status <> com.flashsale.flashsale.entity.SessionStatus.CANCELLED
              and i.status = com.flashsale.flashsale.entity.ItemStatus.APPROVED
            """)
    List<ItemSnapshot> findLiveOrUpcoming(Instant now, Instant horizon);
}
