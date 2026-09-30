package com.flashsale.inventory.service.impl;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.flashsale.inventory.service.InventoryService;
import com.flashsale.outbox.entity.OutboxEvent;
import com.flashsale.outbox.service.EventHandler;

/**
 * Settles flash-sale stock when a slot closes ({@code FLASH_SALE_ITEM_CLOSED {quota, sold}}): sold units leave the
 * stock, unsold units return to available — once per item. Individual purchases ({@code ORDER_CREATED}) do not touch
 * inventory: the quota was locked before the slot, so nothing else can sell those units meanwhile.
 */
@Component
public class InventoryEventHandler implements EventHandler {

    public static final String CONSUMER = "inventory";

    private final InventoryService inventoryService;
    private final JsonMapper jsonMapper;

    public InventoryEventHandler(InventoryService inventoryService, JsonMapper jsonMapper) {
        this.inventoryService = inventoryService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String consumer() {
        return CONSUMER;
    }

    @Override
    public boolean supports(String eventType) {
        return "FLASH_SALE_ITEM_CLOSED".equals(eventType);
    }

    @Override
    public void handle(OutboxEvent event) {
        JsonNode payload = jsonMapper.readTree(event.getPayload());
        inventoryService.settleFlashSaleItem(payload.get("itemId").asLong(), payload.get("productId").asLong(),
                event.getRegion(), payload.get("quota").asInt(), payload.get("sold").asInt(), event.getId());
    }
}
