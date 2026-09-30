package com.flashsale.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.common.security.CurrentUser;
import com.flashsale.config.OpenApiConfig;
import com.flashsale.inventory.dto.InventoryAuditResponse;
import com.flashsale.inventory.service.InventoryAuditService;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@Tag(name = OpenApiConfig.TAG_PLATFORM)
public class InventoryAdminController {

    private final InventoryAuditService auditService;

    public InventoryAdminController(InventoryAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/audit")
    @Operation(summary = "Stock vs. movement ledger for every product of the region, plus outbox lag")
    public InventoryAuditResponse audit(@AuthenticationPrincipal Jwt jwt) {
        return auditService.audit(CurrentUser.from(jwt).region());
    }
}
