package com.flashsale.common.metrics;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Every business metric of the service, in one place (Prometheus names in brackets).
 * Tags are low-cardinality enums — never user ids, emails or item ids.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    private static final List<String> PURCHASE_RESULTS = List.of("success", "replay", "sold_out",
            "already_purchased_today", "flash_sale_not_active", "insufficient_balance", "flash_sale_item_not_found",
            "idempotency_key_required", "idempotency_key_reused", "too_many_requests", "error");
    private static final List<String> GATE_RESULTS = List.of("passed", "sold_out", "already_purchased", "bypassed");
    private static final List<String> LOGIN_RESULTS = List.of("success", "invalid_credentials", "not_verified", "locked");
    private static final List<String> OTP_EVENTS = List.of("issued", "verified", "rejected");

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        // Register the known series at 0 so Prometheus increase()/rate() also count their first event.
        PURCHASE_RESULTS.forEach(r -> Counter.builder("flashsale.purchase").tag("result", r).register(registry));
        GATE_RESULTS.forEach(r -> Counter.builder("flashsale.gate").tag("result", r).register(registry));
        LOGIN_RESULTS.forEach(r -> Counter.builder("auth.login").tag("result", r).register(registry));
        OTP_EVENTS.forEach(e -> Counter.builder("auth.otp").tag("event", e).register(registry));
        Counter.builder("auth.register").register(registry);
        Counter.builder("flashsale.settlement.items").register(registry);
        List.of("rate_limiter", "jwt_blacklist").forEach(c -> Counter.builder("redis.fail_open").tag("component", c).register(registry));
    }

    /** Purchase outcome + latency [flashsale_purchase_total, flashsale_purchase_duration_seconds]. */
    public void purchase(String result, Duration duration) {
        String tag = normalize(result);
        Counter.builder("flashsale.purchase").description("Flash-sale purchase attempts by result")
                .tag("result", tag).register(registry).increment();
        Timer.builder("flashsale.purchase.duration").description("Flash-sale purchase latency")
                .tag("result", tag).register(registry).record(duration);
    }

    /** Redis stock-gate decisions; {@code bypassed} = Redis unavailable, DB-only path [flashsale_gate_total]. */
    public void gate(String result) {
        Counter.builder("flashsale.gate").description("Redis stock gate decisions")
                .tag("result", normalize(result)).register(registry).increment();
    }

    /** Slot generator output [flashsale_generator_slots_total, …items_total, …skipped_total]. */
    public void generated(String region, int slots, int items, int skippedNoStock) {
        Counter.builder("flashsale.generator.slots").tag("region", region).register(registry).increment(slots);
        Counter.builder("flashsale.generator.items").tag("region", region).register(registry).increment(items);
        Counter.builder("flashsale.generator.skipped").description("Occurrences skipped for lack of stock")
                .tag("region", region).register(registry).increment(skippedNoStock);
    }

    /** Items settled at slot end [flashsale_settlement_items_total]. */
    public void settled(int items) {
        Counter.builder("flashsale.settlement.items").register(registry).increment(items);
    }

    /** Outbox events handled [outbox_events_processed_total / outbox_events_failed_total]. */
    public void outboxProcessed(String eventType) {
        Counter.builder("outbox.events.processed").tag("type", eventType).register(registry).increment();
    }

    public void outboxFailed(String eventType) {
        Counter.builder("outbox.events.failed").tag("type", eventType).register(registry).increment();
    }

    /** Login outcome [auth_login_total{result}]. */
    public void login(String result) {
        Counter.builder("auth.login").tag("result", normalize(result)).register(registry).increment();
    }

    /** OTP lifecycle: issued / verified / rejected [auth_otp_total{event}]. */
    public void otp(String event) {
        Counter.builder("auth.otp").tag("event", event).register(registry).increment();
    }

    public void registration() {
        Counter.builder("auth.register").register(registry).increment();
    }

    /** A Redis-backed check was skipped because Redis is unavailable [redis_fail_open_total{component}]. */
    public void redisFailOpen(String component) {
        Counter.builder("redis.fail_open").description("Checks skipped because Redis was unavailable")
                .tag("component", component).register(registry).increment();
    }

    /** Requests rejected by a rate-limit rule [ratelimit_rejected_total{scope}]. */
    public void rateLimited(String scope) {
        Counter.builder("ratelimit.rejected").tag("scope", scope).register(registry).increment();
    }

    /** Warehouse stock events by outcome [warehouse_sync_events_total{status}]. */
    public void warehouseEvent(String status) {
        Counter.builder("warehouse.sync.events").tag("status", normalize(status)).register(registry).increment();
    }

    private static String normalize(String value) {
        return value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
}
