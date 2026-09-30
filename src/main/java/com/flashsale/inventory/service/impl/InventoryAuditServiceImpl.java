package com.flashsale.inventory.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.inventory.dto.InventoryAuditResponse;
import com.flashsale.inventory.dto.InventoryAuditRow;
import com.flashsale.inventory.service.InventoryAuditService;
import com.flashsale.outbox.entity.OutboxStatus;
import com.flashsale.outbox.repository.OutboxEventRepository;

@Service
public class InventoryAuditServiceImpl implements InventoryAuditService {

    private static final String AUDIT = """
            SELECT p.id, p.sku, v.total, v.available, v.reserved,
                   COALESCE(SUM(m.delta) FILTER (WHERE m.reason IN ('RESTOCK','ADJUST','WAREHOUSE_SYNC','PURCHASE')), 0)
                       AS ledger_total,
                   COALESCE(SUM(m.delta) FILTER (WHERE m.reason IN ('RESTOCK','ADJUST','WAREHOUSE_SYNC','RESERVE','RELEASE')), 0)
                       AS ledger_available
            FROM products p
            JOIN inventory v ON v.product_id = p.id
            LEFT JOIN inventory_movements m ON m.product_id = p.id
            WHERE p.region = ?
            GROUP BY p.id, p.sku, v.total, v.available, v.reserved
            ORDER BY p.id
            """;

    private final JdbcTemplate jdbc;
    private final OutboxEventRepository outbox;

    public InventoryAuditServiceImpl(JdbcTemplate jdbc, OutboxEventRepository outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryAuditResponse audit(String region) {
        List<InventoryAuditRow> rows = jdbc.query(AUDIT, (rs, i) -> {
            int total = rs.getInt("total");
            int available = rs.getInt("available");
            long ledgerTotal = rs.getLong("ledger_total");
            long ledgerAvailable = rs.getLong("ledger_available");
            return new InventoryAuditRow(rs.getLong("id"), rs.getString("sku"), total, available,
                    rs.getInt("reserved"), ledgerTotal, ledgerAvailable,
                    total == ledgerTotal && available == ledgerAvailable);
        }, region);
        return new InventoryAuditResponse(Instant.now(), region,
                outbox.countByRegionAndStatus(region, OutboxStatus.PENDING),
                outbox.countByRegionAndStatus(region, OutboxStatus.FAILED),
                rows.stream().allMatch(InventoryAuditRow::consistent), rows);
    }
}
