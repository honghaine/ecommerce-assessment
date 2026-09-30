package com.flashsale.flashsale.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.common.security.CurrentUser;
import com.flashsale.config.OpenApiConfig;
import com.flashsale.flashsale.dto.FlashSaleConfigResponse;
import com.flashsale.flashsale.dto.RuleRequest;
import com.flashsale.flashsale.dto.RuleResponse;
import com.flashsale.flashsale.dto.SellerItemResponse;
import com.flashsale.flashsale.entity.RuleStatus;
import com.flashsale.flashsale.service.PlatformFlashSaleService;
import com.flashsale.flashsale.service.SellerFlashSaleService;

@RestController
@RequestMapping("/api/v1/seller/flash-sales")
@Tag(name = OpenApiConfig.TAG_SELLER)
public class SellerFlashSaleController {

    private final SellerFlashSaleService sellerService;
    private final PlatformFlashSaleService platformService;

    public SellerFlashSaleController(SellerFlashSaleService sellerService, PlatformFlashSaleService platformService) {
        this.sellerService = sellerService;
        this.platformService = platformService;
    }

    @GetMapping("/config")
    @Operation(summary = "Region flash-sale schedule (read-only)",
            description = "Window length and active weekdays — rule start times must match these windows.")
    public FlashSaleConfigResponse config(@AuthenticationPrincipal Jwt jwt) {
        return platformService.getConfig(CurrentUser.from(jwt).region());
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule an own product: slot start time × weekdays",
            description = """
                    Example: productId 3 at 12:00 on MONDAY/WEDNESDAY/FRIDAY, price 3,000,000, quota 20 per slot. \\
                    Slots/items for the configured horizon are generated immediately; quota is reserved from \\
                    stock per occurrence (occurrences without enough stock are skipped).""")
    public RuleResponse createRule(@Valid @RequestBody RuleRequest body, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser seller = CurrentUser.from(jwt);
        return sellerService.createRule(seller.id(), seller.region(), body);
    }

    @GetMapping("/rules")
    @Operation(summary = "List own rules (active and paused)")
    public List<RuleResponse> listRules(@AuthenticationPrincipal Jwt jwt) {
        return sellerService.listRules(CurrentUser.from(jwt).id());
    }

    @PutMapping("/rules/{ruleId}")
    @Operation(summary = "Change time, weekdays, price or quota",
            description = "Not-yet-started occurrences are regenerated from the new rule; running slots are untouched.")
    public RuleResponse updateRule(@PathVariable long ruleId, @Valid @RequestBody RuleRequest body,
                                   @AuthenticationPrincipal Jwt jwt) {
        return sellerService.updateRule(CurrentUser.from(jwt).id(), ruleId, body);
    }

    @PostMapping("/rules/{ruleId}/pause")
    @Operation(summary = "Pause a rule (future occurrences removed, stock returned)")
    public RuleResponse pause(@PathVariable long ruleId, @AuthenticationPrincipal Jwt jwt) {
        return sellerService.changeStatus(CurrentUser.from(jwt).id(), ruleId, RuleStatus.PAUSED);
    }

    @PostMapping("/rules/{ruleId}/resume")
    @Operation(summary = "Resume a paused rule (occurrences regenerated)")
    public RuleResponse resume(@PathVariable long ruleId, @AuthenticationPrincipal Jwt jwt) {
        return sellerService.changeStatus(CurrentUser.from(jwt).id(), ruleId, RuleStatus.ACTIVE);
    }

    @PostMapping("/rules/{ruleId}/archive")
    @Operation(summary = "Archive a rule permanently (future occurrences removed, stock returned)")
    public RuleResponse archive(@PathVariable long ruleId, @AuthenticationPrincipal Jwt jwt) {
        return sellerService.changeStatus(CurrentUser.from(jwt).id(), ruleId, RuleStatus.ARCHIVED);
    }

    @GetMapping("/items")
    @Operation(summary = "Own flash-sale occurrences of a day (region-local)", description = "Defaults to today.")
    public List<SellerItemResponse> items(
            @Parameter(example = "2026-09-30") @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser seller = CurrentUser.from(jwt);
        return sellerService.listItems(seller.id(), seller.region(), date);
    }

    @PostMapping("/items/{itemId}/withdraw")
    @Operation(summary = "Skip one occurrence before its slot starts (stock returned, not regenerated)")
    public SellerItemResponse withdraw(@PathVariable long itemId, @AuthenticationPrincipal Jwt jwt) {
        return sellerService.withdrawItem(CurrentUser.from(jwt).id(), itemId);
    }
}
