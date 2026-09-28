-- =====================================================================
-- FlashSale Service schema
--  * All timestamps UTC (timestamptz). Business "day" = region-local date.
--  * Every business table carries `region` (market code). Region metadata
--    (timezone, currency) lives in app config (app.regions.*).
--  * Parent tables expose UNIQUE (id, region); children reference
--    (parent_id, region) so related rows can never cross regions.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Users / auth
-- ---------------------------------------------------------------------
CREATE TABLE users
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region        VARCHAR(8)   NOT NULL,
    email         VARCHAR(254),
    phone         VARCHAR(20),
    password_hash VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone),
    CONSTRAINT uq_users_id_region UNIQUE (id, region),
    CONSTRAINT ck_users_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_users_identifier CHECK (email IS NOT NULL OR phone IS NOT NULL),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED')),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'SELLER', 'PLATFORM_ADMIN'))
);

CREATE TABLE refresh_tokens
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region     VARCHAR(8)  NOT NULL,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id, region) REFERENCES users (id, region),
    CONSTRAINT ck_refresh_tokens_region CHECK (region ~ '^[A-Z]{2,8}$')
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE notification_outbox
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region     VARCHAR(8)   NOT NULL,
    channel    VARCHAR(10)  NOT NULL,
    recipient  VARCHAR(254) NOT NULL,
    template   VARCHAR(50)  NOT NULL,
    payload    JSONB        NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at    TIMESTAMPTZ,
    CONSTRAINT ck_notification_outbox_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_notification_outbox_channel CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT ck_notification_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX idx_notification_outbox_pending ON notification_outbox (created_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------
-- Wallet
-- ---------------------------------------------------------------------
CREATE TABLE wallets
(
    user_id    BIGINT PRIMARY KEY,
    region     VARCHAR(8)     NOT NULL,
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version    BIGINT         NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id, region) REFERENCES users (id, region),
    CONSTRAINT ck_wallets_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_wallets_balance CHECK (balance >= 0)
);

-- ---------------------------------------------------------------------
-- Catalog / inventory
-- ---------------------------------------------------------------------
CREATE TABLE products
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region      VARCHAR(8)     NOT NULL,
    seller_id   BIGINT         NOT NULL,
    sku         VARCHAR(64)    NOT NULL,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       NUMERIC(19, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_region_sku UNIQUE (region, sku),
    CONSTRAINT uq_products_id_region UNIQUE (id, region),
    CONSTRAINT uq_products_id_seller_region UNIQUE (id, seller_id, region),
    CONSTRAINT fk_products_seller FOREIGN KEY (seller_id, region) REFERENCES users (id, region),
    CONSTRAINT ck_products_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX idx_products_seller ON products (seller_id);

CREATE TABLE inventory
(
    product_id BIGINT PRIMARY KEY,
    region     VARCHAR(8)  NOT NULL,
    total      INT         NOT NULL DEFAULT 0,
    available  INT         NOT NULL DEFAULT 0,
    reserved   INT         NOT NULL DEFAULT 0,
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id, region) REFERENCES products (id, region),
    CONSTRAINT ck_inventory_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_inventory_available CHECK (available >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_total CHECK (total >= 0)
);

CREATE TABLE inventory_movements
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region       VARCHAR(8)  NOT NULL,
    product_id   BIGINT      NOT NULL,
    delta        INT         NOT NULL,
    reason       VARCHAR(20) NOT NULL,
    ref_event_id UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_movements_event UNIQUE (ref_event_id),
    CONSTRAINT fk_inventory_movements_product FOREIGN KEY (product_id, region) REFERENCES products (id, region),
    CONSTRAINT ck_inventory_movements_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_inventory_movements_reason CHECK (reason IN ('PURCHASE', 'RESTOCK', 'RESERVE', 'RELEASE', 'ADJUST'))
);
CREATE INDEX idx_inventory_movements_product ON inventory_movements (product_id, created_at);

-- ---------------------------------------------------------------------
-- Flash sale
-- ---------------------------------------------------------------------
CREATE TABLE flash_sale_sessions
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region     VARCHAR(8)   NOT NULL,
    type       VARCHAR(10)  NOT NULL DEFAULT 'PLATFORM',
    seller_id  BIGINT,
    created_by BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    sale_date  DATE         NOT NULL,
    start_at   TIMESTAMPTZ  NOT NULL,
    end_at     TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_flash_sale_sessions_id_region UNIQUE (id, region),
    CONSTRAINT fk_flash_sale_sessions_seller FOREIGN KEY (seller_id, region) REFERENCES users (id, region),
    CONSTRAINT fk_flash_sale_sessions_creator FOREIGN KEY (created_by, region) REFERENCES users (id, region),
    CONSTRAINT ck_flash_sale_sessions_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_flash_sale_sessions_window CHECK (end_at > start_at),
    CONSTRAINT ck_flash_sale_sessions_type CHECK (
        (type = 'PLATFORM' AND seller_id IS NULL) OR (type = 'SHOP' AND seller_id IS NOT NULL)),
    CONSTRAINT ck_flash_sale_sessions_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'ENDED', 'CANCELLED'))
);
CREATE INDEX idx_flash_sale_sessions_window ON flash_sale_sessions (region, start_at, end_at);

CREATE TABLE flash_sale_items
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region      VARCHAR(8)     NOT NULL,
    session_id  BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    seller_id   BIGINT         NOT NULL,
    sale_price  NUMERIC(19, 2) NOT NULL,
    quota       INT            NOT NULL,
    sold        INT            NOT NULL DEFAULT 0,
    status      VARCHAR(20)    NOT NULL DEFAULT 'APPROVED',
    reviewed_by BIGINT,
    reviewed_at TIMESTAMPTZ,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_flash_sale_items_session_product UNIQUE (session_id, product_id),
    CONSTRAINT uq_flash_sale_items_id_region UNIQUE (id, region),
    CONSTRAINT fk_flash_sale_items_session FOREIGN KEY (session_id, region) REFERENCES flash_sale_sessions (id, region),
    -- seller can only nominate their OWN product, in the same region
    CONSTRAINT fk_flash_sale_items_product FOREIGN KEY (product_id, seller_id, region) REFERENCES products (id, seller_id, region),
    CONSTRAINT fk_flash_sale_items_reviewer FOREIGN KEY (reviewed_by, region) REFERENCES users (id, region),
    CONSTRAINT ck_flash_sale_items_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_flash_sale_items_price CHECK (sale_price > 0),
    CONSTRAINT ck_flash_sale_items_quota CHECK (quota >= 0),
    CONSTRAINT ck_flash_sale_items_sold CHECK (sold >= 0 AND sold <= quota),
    CONSTRAINT ck_flash_sale_items_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);
CREATE INDEX idx_flash_sale_items_session_seller ON flash_sale_items (session_id, seller_id);

-- ---------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------
CREATE TABLE orders
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region             VARCHAR(8)     NOT NULL,
    user_id            BIGINT         NOT NULL,
    flash_sale_item_id BIGINT         NOT NULL,
    product_id         BIGINT         NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL,
    quantity           INT            NOT NULL DEFAULT 1,
    status             VARCHAR(20)    NOT NULL,
    idempotency_key    VARCHAR(64)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_orders_id_region UNIQUE (id, region),
    -- buyer and item must be in the same region
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id, region) REFERENCES users (id, region),
    CONSTRAINT fk_orders_item FOREIGN KEY (flash_sale_item_id, region) REFERENCES flash_sale_items (id, region),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id, region) REFERENCES products (id, region),
    CONSTRAINT ck_orders_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_orders_amount CHECK (amount > 0),
    CONSTRAINT ck_orders_quantity CHECK (quantity = 1),
    CONSTRAINT ck_orders_status CHECK (status IN ('CREATED', 'PAID', 'CANCELLED'))
);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
CREATE INDEX idx_orders_region_created ON orders (region, created_at);

-- "1 flash-sale product per user per region-local day"
CREATE TABLE user_daily_purchases
(
    user_id       BIGINT      NOT NULL,
    purchase_date DATE        NOT NULL,
    region        VARCHAR(8)  NOT NULL,
    order_id      BIGINT      NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_daily_purchases PRIMARY KEY (user_id, purchase_date),
    CONSTRAINT uq_user_daily_purchases_order UNIQUE (order_id),
    CONSTRAINT fk_user_daily_purchases_user FOREIGN KEY (user_id, region) REFERENCES users (id, region),
    CONSTRAINT fk_user_daily_purchases_order FOREIGN KEY (order_id, region) REFERENCES orders (id, region),
    CONSTRAINT ck_user_daily_purchases_region CHECK (region ~ '^[A-Z]{2,8}$')
);

CREATE TABLE wallet_transactions
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region     VARCHAR(8)     NOT NULL,
    user_id    BIGINT         NOT NULL,
    order_id   BIGINT,
    amount     NUMERIC(19, 2) NOT NULL,
    type       VARCHAR(20)    NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fk_wallet_transactions_user FOREIGN KEY (user_id, region) REFERENCES users (id, region),
    CONSTRAINT fk_wallet_transactions_order FOREIGN KEY (order_id, region) REFERENCES orders (id, region),
    CONSTRAINT ck_wallet_transactions_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_wallet_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_wallet_transactions_type CHECK (type IN ('DEBIT', 'CREDIT', 'REFUND'))
);
CREATE INDEX idx_wallet_transactions_user ON wallet_transactions (user_id, created_at);

-- ---------------------------------------------------------------------
-- Domain event outbox (inventory sync etc.)
-- ---------------------------------------------------------------------
CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    region         VARCHAR(8)  NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    payload        JSONB       NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts       INT         NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,
    CONSTRAINT ck_outbox_events_region CHECK (region ~ '^[A-Z]{2,8}$'),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);
CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at) WHERE status = 'PENDING';

CREATE TABLE processed_events
(
    consumer     VARCHAR(50) NOT NULL,
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer, event_id)
);
