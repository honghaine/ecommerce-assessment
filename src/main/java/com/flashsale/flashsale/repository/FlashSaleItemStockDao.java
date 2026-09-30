package com.flashsale.flashsale.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The oversell guard. One atomic statement checks slot window (DB clock), approval, product status and remaining
 * quota and increments {@code sold}; row locks serialize concurrent buyers of the same item.
 * Runs on the JPA transaction's connection (JpaTransactionManager exposes it to JDBC).
 */
@Repository
public class FlashSaleItemStockDao {

    private static final String CLAIM_ONE = """
            UPDATE flash_sale_items i
            SET sold = i.sold + 1, updated_at = now()
            FROM flash_sale_sessions s, products p
            WHERE i.id = :itemId
              AND i.session_id = s.id
              AND p.id = i.product_id AND p.status = 'ACTIVE'
              AND i.region = :region
              AND i.status = 'APPROVED'
              AND s.status <> 'CANCELLED'
              AND now() >= s.start_at AND now() < s.end_at
              AND i.sold < i.quota
            RETURNING i.sale_price, i.product_id, s.sale_date
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FlashSaleItemStockDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the claimed unit, or empty if sold out / not live / not approved / other region */
    public Optional<ClaimedUnit> claimOne(long itemId, String region) {
        List<ClaimedUnit> rows = jdbc.query(CLAIM_ONE,
                new MapSqlParameterSource().addValue("itemId", itemId).addValue("region", region),
                (rs, rowNum) -> new ClaimedUnit(rs.getBigDecimal("sale_price"), rs.getLong("product_id"),
                        rs.getObject("sale_date", LocalDate.class)));
        return rows.stream().findFirst();
    }

    public record ClaimedUnit(BigDecimal salePrice, long productId, LocalDate saleDate) {
    }
}
