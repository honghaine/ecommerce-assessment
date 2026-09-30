package com.flashsale.common.metrics;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Health of asynchronous processing and data consistency, sampled from the database every 30 s:
 * <ul>
 *   <li>{@code outbox_pending_events}, {@code outbox_failed_events} — backlog and dead letters;</li>
 *   <li>{@code outbox_lag_seconds} — age of the oldest pending event (how far behind the consumers are);</li>
 *   <li>{@code inventory_drift_products} — products whose stock disagrees with the movement ledger (must be 0).</li>
 * </ul>
 * Every instance reports the same (global) values — dashboards use {@code max()}.
 */
@Slf4j
@Component
public class StateGauges {

    private static final String DRIFT = """
            SELECT count(*) FROM (
              SELECT v.product_id
              FROM inventory v LEFT JOIN inventory_movements m ON m.product_id = v.product_id
              GROUP BY v.product_id, v.total, v.available
              HAVING v.total <> COALESCE(SUM(m.delta) FILTER (WHERE m.reason IN ('RESTOCK','ADJUST','WAREHOUSE_SYNC','PURCHASE')), 0)
                  OR v.available <> COALESCE(SUM(m.delta) FILTER (WHERE m.reason IN ('RESTOCK','ADJUST','WAREHOUSE_SYNC','RESERVE','RELEASE')), 0)
            ) drift
            """;

    private final JdbcTemplate jdbc;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong lagSeconds = new AtomicLong();
    private final AtomicLong drift = new AtomicLong();

    public StateGauges(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("outbox.pending.events", pending, AtomicLong::get).description("Outbox events waiting").register(registry);
        Gauge.builder("outbox.failed.events", failed, AtomicLong::get).description("Dead-lettered outbox events").register(registry);
        Gauge.builder("outbox.lag.seconds", lagSeconds, AtomicLong::get).description("Age of the oldest pending event").register(registry);
        Gauge.builder("inventory.drift.products", drift, AtomicLong::get)
                .description("Products whose stock disagrees with the ledger").register(registry);
    }

    @Scheduled(fixedDelayString = "${app.metrics.state-interval-ms:30000}", initialDelayString = "20000")
    public void sample() {
        try {
            jdbc.query("""
                    SELECT count(*) FILTER (WHERE status = 'PENDING') AS pending,
                           count(*) FILTER (WHERE status = 'FAILED') AS failed,
                           COALESCE(EXTRACT(EPOCH FROM now() - min(created_at) FILTER (WHERE status = 'PENDING')), 0) AS lag
                    FROM outbox_events WHERE status <> 'PROCESSED'
                    """, rs -> {
                pending.set(rs.getLong("pending"));
                failed.set(rs.getLong("failed"));
                lagSeconds.set((long) rs.getDouble("lag"));
            });
            Long driftCount = jdbc.queryForObject(DRIFT, Long.class);
            drift.set(driftCount == null ? 0 : driftCount);
        } catch (DataAccessException ex) {
            log.warn("State gauge sampling failed: {}", ex.getMessage());
        }
    }
}
