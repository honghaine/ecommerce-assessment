package com.flashsale.flashsale.service.impl;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.flashsale.flashsale.entity.RuleStatus;
import com.flashsale.flashsale.entity.SellerFlashSaleRule;
import com.flashsale.flashsale.repository.SellerFlashSaleRuleRepository;
import com.flashsale.flashsale.support.RuleOccurrenceCleaner;
import com.flashsale.outbox.entity.OutboxEvent;
import com.flashsale.outbox.service.EventHandler;

/**
 * Keeps flash sales (and their reserved stock) in sync with product changes:
 * product INACTIVE → pause all its rules; new price ≤ a rule's sale price → pause that rule.
 * Paused rules' not-yet-started occurrences are removed and their stock returned to available.
 * Idempotent: an already-paused rule is left as is, and the dispatcher dedupes the event itself.
 */
@Slf4j
@Component
public class ProductChangeHandler implements EventHandler {

    private final SellerFlashSaleRuleRepository rules;
    private final RuleOccurrenceCleaner occurrenceCleaner;
    private final JsonMapper jsonMapper;

    public ProductChangeHandler(SellerFlashSaleRuleRepository rules, RuleOccurrenceCleaner occurrenceCleaner,
                                JsonMapper jsonMapper) {
        this.rules = rules;
        this.occurrenceCleaner = occurrenceCleaner;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String consumer() {
        return "flash-sale";
    }

    @Override
    public boolean supports(String eventType) {
        return "PRODUCT_CHANGED".equals(eventType);
    }

    @Override
    public void handle(OutboxEvent event) {
        JsonNode payload = jsonMapper.readTree(event.getPayload());
        long productId = payload.get("productId").asLong();
        boolean inactive = "INACTIVE".equals(payload.get("status").asString());
        BigDecimal price = payload.get("price").decimalValue();

        for (SellerFlashSaleRule rule : rules.findByProductIdAndStatus(productId, RuleStatus.ACTIVE)) {
            if (inactive || rule.getSalePrice().compareTo(price) >= 0) {
                int removed = occurrenceCleaner.removeFutureItems(rule.getId());
                rule.changeStatus(RuleStatus.PAUSED);
                rules.save(rule);
                log.info("Paused rule {} of product {} ({}), removed {} future occurrences", rule.getId(), productId,
                        inactive ? "product inactive" : "price no longer above sale price", removed);
            }
        }
    }
}
