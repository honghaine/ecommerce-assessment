package com.flashsale.flashsale.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.common.security.CurrentUser;
import com.flashsale.config.OpenApiConfig;
import com.flashsale.flashsale.dto.AdminSessionView;
import com.flashsale.flashsale.dto.FlashSaleConfigRequest;
import com.flashsale.flashsale.dto.FlashSaleConfigResponse;
import com.flashsale.flashsale.model.GenerationResult;
import com.flashsale.flashsale.service.PlatformFlashSaleService;

/** Platform admin: manages the flash-sale schedule of their own region. */
@RestController
@RequestMapping("/api/v1/admin/flash-sales")
@Tag(name = OpenApiConfig.TAG_PLATFORM)
public class PlatformFlashSaleController {

    private final PlatformFlashSaleService platformService;

    public PlatformFlashSaleController(PlatformFlashSaleService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/config")
    @Operation(summary = "Region flash-sale schedule",
            description = "Default: every day, 60-minute windows (24 per day), 2 days generated ahead.")
    public FlashSaleConfigResponse getConfig(@AuthenticationPrincipal Jwt jwt) {
        return platformService.getConfig(CurrentUser.from(jwt).region());
    }

    @PutMapping("/config")
    @Operation(summary = "Update the schedule",
            description = "Applies to slots generated from now on (already generated slots are kept). "
                    + "Seller rules whose start time no longer matches a window stop producing items.")
    public FlashSaleConfigResponse updateConfig(@Valid @RequestBody FlashSaleConfigRequest body,
                                                @AuthenticationPrincipal Jwt jwt) {
        CurrentUser admin = CurrentUser.from(jwt);
        return platformService.updateConfig(admin.region(), admin.id(), body);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Slots of a day with every seller's items, grouped by seller",
            description = "Region-local date; defaults to today.")
    public List<AdminSessionView> sessions(
            @Parameter(example = "2026-09-30") @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal Jwt jwt) {
        return platformService.listSessions(CurrentUser.from(jwt).region(), date);
    }

    @PostMapping("/generate")
    @Operation(summary = "Run slot/item generation now (normally every 10 minutes)")
    public GenerationResult generate(@AuthenticationPrincipal Jwt jwt) {
        return platformService.generateNow(CurrentUser.from(jwt).region());
    }
}
