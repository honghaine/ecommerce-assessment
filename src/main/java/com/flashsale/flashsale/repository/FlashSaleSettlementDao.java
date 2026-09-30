package com.flashsale.flashsale.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Closes items whose slot has ended. The guarded UPDATE takes the item row lock — the same lock purchases take —
 * so a purchase committing at the slot boundary is always counted in {@code sold} before settlement reads it,
 * and {@code settled_at IS NULL} makes settlement happen exactly once even with several instances.
 */
@Repository
public class FlashSaleSettlementDao {

    private final JdbcTemplate jdbc;

    public FlashSaleSettlementDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> findEndedUnsettled(int limit) {
        return jdbc.queryForList("""
                SELECT i.id FROM flash_sale_items i
                JOIN flash_sale_sessions s ON s.id = i.session_id
                WHERE i.settled_at IS NULL AND s.end_at <= now()
                ORDER BY s.end_at, i.id
                LIMIT ?
                """, Long.class, limit);
    }

    /** @return the settled item, or empty if another instance already settled it */
    public Optional<SettledItem> settle(long itemId) {
        return jdbc.query("""
                UPDATE flash_sale_items i SET settled_at = now(), updated_at = now()
                FROM flash_sale_sessions s
                WHERE i.id = ? AND i.session_id = s.id AND i.settled_at IS NULL AND s.end_at <= now()
                RETURNING i.id, i.region, i.product_id, i.status, i.quota, i.sold
                """, (rs, n) -> new SettledItem(rs.getLong("id"), rs.getString("region"), rs.getLong("product_id"),
                rs.getString("status"), rs.getInt("quota"), rs.getInt("sold")), itemId).stream().findFirst();
    }

    public record SettledItem(long itemId, String region, long productId, String status, int quota, int sold) {
    }
}
