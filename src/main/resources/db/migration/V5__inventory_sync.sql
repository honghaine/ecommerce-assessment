-- =====================================================================
-- Inventory sync (requirement 2.3)
-- =====================================================================

-- Stock buckets must always add up: total = available (sellable) + reserved (flash-sale quotas).
ALTER TABLE inventory ADD CONSTRAINT ck_inventory_balance CHECK (available + reserved = total);

-- Ledger: warehouse sync reason + idempotency key for client-driven changes (restock).
ALTER TABLE inventory_movements DROP CONSTRAINT ck_inventory_movements_reason;
ALTER TABLE inventory_movements ADD CONSTRAINT ck_inventory_movements_reason
    CHECK (reason IN ('PURCHASE', 'RESTOCK', 'RESERVE', 'RELEASE', 'ADJUST', 'WAREHOUSE_SYNC'));
ALTER TABLE inventory_movements ADD COLUMN idempotency_key VARCHAR(64);
CREATE UNIQUE INDEX uq_inventory_movements_idempotency
    ON inventory_movements (product_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Slot settlement: unsold quota goes back to available stock exactly once per item.
ALTER TABLE flash_sale_items ADD COLUMN settled_at TIMESTAMPTZ;
CREATE INDEX idx_flash_sale_items_unsettled ON flash_sale_items (session_id) WHERE settled_at IS NULL;

-- Outbox retries with backoff.
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
DROP INDEX idx_outbox_events_pending;
CREATE INDEX idx_outbox_events_due ON outbox_events (next_attempt_at, created_at) WHERE status = 'PENDING';

-- External warehouse (WMS) stock deltas: one row per external event → applied at most once.
CREATE TABLE inventory_sync_log
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source            VARCHAR(50)  NOT NULL,
    external_event_id VARCHAR(100) NOT NULL,
    region            VARCHAR(8)   NOT NULL,
    sku               VARCHAR(64)  NOT NULL,
    product_id        BIGINT,
    delta             INT          NOT NULL,
    reason            VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    detail            VARCHAR(200),
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_sync_log_event UNIQUE (source, external_event_id),
    CONSTRAINT ck_inventory_sync_log_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_inventory_sync_log_reason CHECK (reason IN ('RECEIVED', 'DAMAGED', 'RETURNED', 'CORRECTION')),
    CONSTRAINT ck_inventory_sync_log_status CHECK (status IN ('APPLIED', 'REJECTED'))
);
CREATE INDEX idx_inventory_sync_log_product ON inventory_sync_log (product_id, received_at);
