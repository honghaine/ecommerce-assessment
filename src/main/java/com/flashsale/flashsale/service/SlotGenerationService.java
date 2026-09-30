package com.flashsale.flashsale.service;

import com.flashsale.flashsale.model.GenerationResult;

/**
 * Materialises flash-sale slots and items from DB configuration:
 * {@code flash_sale_configs} (windows per day, active weekdays, horizon) and
 * {@code seller_flash_sale_rules} (product × start time × weekdays). Idempotent.
 */
public interface SlotGenerationService {

    GenerationResult generate(String region);
}
