-- One platform slot per region + start time: makes slot seeding/creation idempotent across instances.
CREATE UNIQUE INDEX uq_flash_sale_sessions_platform_slot
    ON flash_sale_sessions (region, start_at)
    WHERE type = 'PLATFORM';
