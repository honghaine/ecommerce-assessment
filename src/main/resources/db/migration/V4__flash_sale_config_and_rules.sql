-- =====================================================================
-- Flash-sale schedule configuration (per region) + seller recurring rules.
-- Slots (flash_sale_sessions) and items (flash_sale_items) are generated
-- from these rows by the slot generator — nothing is hard-coded.
-- =====================================================================

-- Platform schedule per region. Default: every day, 1h windows (24 per day), 2 days generated ahead.
CREATE TABLE flash_sale_configs
(
    region       VARCHAR(8)  PRIMARY KEY,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    slot_minutes INT         NOT NULL DEFAULT 60,
    active_days  SMALLINT    NOT NULL DEFAULT 127,   -- bit 0 = Monday … bit 6 = Sunday
    horizon_days INT         NOT NULL DEFAULT 2,
    updated_by   BIGINT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_flash_sale_configs_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_flash_sale_configs_slot CHECK (slot_minutes BETWEEN 15 AND 1440 AND 1440 % slot_minutes = 0),
    CONSTRAINT ck_flash_sale_configs_days CHECK (active_days BETWEEN 0 AND 127),
    CONSTRAINT ck_flash_sale_configs_horizon CHECK (horizon_days BETWEEN 1 AND 14),
    CONSTRAINT fk_flash_sale_configs_updated_by FOREIGN KEY (updated_by, region) REFERENCES users (id, region)
);

INSERT INTO flash_sale_configs (region) VALUES ('VN'), ('TH'), ('SG');

-- Seller rule: "put product P on flash sale at HH:MM on these weekdays, price X, quota Q per slot".
CREATE TABLE seller_flash_sale_rules
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region          VARCHAR(8)     NOT NULL,
    seller_id       BIGINT         NOT NULL,
    product_id      BIGINT         NOT NULL,
    slot_start_time TIME           NOT NULL,          -- region-local start of the window
    days_of_week    SMALLINT       NOT NULL,          -- bit 0 = Monday … bit 6 = Sunday
    sale_price      NUMERIC(19, 2) NOT NULL,
    quota           INT            NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_seller_flash_sale_rules_id_region UNIQUE (id, region),
    -- seller can only schedule their OWN product, in their region
    CONSTRAINT fk_seller_flash_sale_rules_product FOREIGN KEY (product_id, seller_id, region)
        REFERENCES products (id, seller_id, region),
    CONSTRAINT ck_seller_flash_sale_rules_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_seller_flash_sale_rules_days CHECK (days_of_week BETWEEN 1 AND 127),
    CONSTRAINT ck_seller_flash_sale_rules_price CHECK (sale_price > 0),
    CONSTRAINT ck_seller_flash_sale_rules_quota CHECK (quota > 0),
    CONSTRAINT ck_seller_flash_sale_rules_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED'))
);
-- one live rule per product per start time (days are combined in the mask)
CREATE UNIQUE INDEX uq_seller_flash_sale_rules_product_time
    ON seller_flash_sale_rules (product_id, slot_start_time) WHERE status <> 'ARCHIVED';
CREATE INDEX idx_seller_flash_sale_rules_region_status ON seller_flash_sale_rules (region, status);
CREATE INDEX idx_seller_flash_sale_rules_seller ON seller_flash_sale_rules (seller_id);

-- Generated items remember their rule; generated slots have no human creator.
ALTER TABLE flash_sale_items ADD COLUMN rule_id BIGINT;
ALTER TABLE flash_sale_items ADD CONSTRAINT fk_flash_sale_items_rule
    FOREIGN KEY (rule_id, region) REFERENCES seller_flash_sale_rules (id, region);
CREATE INDEX idx_flash_sale_items_rule ON flash_sale_items (rule_id);

ALTER TABLE flash_sale_sessions ALTER COLUMN created_by DROP NOT NULL;   -- NULL = generated from config
