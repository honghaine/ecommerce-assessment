package com.flashsale.flashsale.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.security.CurrentUser;
import com.flashsale.config.OpenApiConfig;
import com.flashsale.flashsale.dto.CurrentFlashSaleResponse;
import com.flashsale.flashsale.dto.PurchaseResponse;
import com.flashsale.flashsale.model.PurchaseResult;
import com.flashsale.flashsale.service.FlashSaleQueryService;
import com.flashsale.flashsale.service.PurchaseService;
import com.flashsale.region.service.RegionService;

@RestController
@RequestMapping("/api/v1/flash-sales")
@Tag(name = OpenApiConfig.TAG_FLASH_SALE)
public class FlashSaleController {

    private static final String PROBLEM = "application/problem+json";

    private final FlashSaleQueryService queryService;
    private final PurchaseService purchaseService;
    private final RegionService regionService;

    public FlashSaleController(FlashSaleQueryService queryService, PurchaseService purchaseService,
                               RegionService regionService) {
        this.queryService = queryService;
        this.purchaseService = purchaseService;
        this.regionService = regionService;
    }

    @GetMapping("/current")
    @SecurityRequirements
    @Operation(summary = "Products on flash sale now",
            description = """
                    Public. Logged-in callers (Bearer token) get their own region; anonymous callers must pass \\
                    `region`. Cached for ~2 s, so `remaining` may lag slightly.""")
    @ApiResponse(responseCode = "200", description = "Live slots with their items")
    @ApiResponse(responseCode = "400", description = "UNSUPPORTED_REGION",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    public CurrentFlashSaleResponse current(
            @Parameter(description = "Market code; ignored when a Bearer token is sent", example = "VN")
            @RequestParam(required = false) String region,
            @AuthenticationPrincipal Jwt jwt) {
        String effectiveRegion = jwt != null ? CurrentUser.from(jwt).region() : region;
        if (effectiveRegion == null || effectiveRegion.isBlank()) {
            throw new ApiException(ErrorCode.UNSUPPORTED_REGION);
        }
        return queryService.current(effectiveRegion);
    }

    @PostMapping("/items/{itemId}/purchase")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Buy one unit of a flash-sale item",
            description = """
                    Buyer only (role USER). Allowed only while the item's slot is live. One flash-sale product \\
                    per user per region-local day. Send a unique `Idempotency-Key` per purchase attempt; \\
                    retrying with the same key returns the original order (200).""")
    @ApiResponse(responseCode = "201", description = "Purchased (order PAID, balance debited)")
    @ApiResponse(responseCode = "200", description = "Replay of an earlier purchase with the same Idempotency-Key")
    @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_REQUIRED",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "FLASH_SALE_ITEM_NOT_FOUND",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "FLASH_SALE_NOT_ACTIVE / SOLD_OUT / ALREADY_PURCHASED_TODAY / IDEMPOTENCY_KEY_REUSED",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_BALANCE",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS",
            content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PurchaseResponse> purchase(
            @PathVariable long itemId,
            @Parameter(description = "Unique per purchase attempt (8-64 chars: letters, digits, '-'), e.g. a UUID",
                    example = "3f6c1a52-8d1e-4b7a-9c2f-5e8a7b6d4c3e")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        PurchaseResult result = purchaseService.purchase(user.id(), user.region(), itemId, idempotencyKey);
        String currency = regionService.currency(user.region()).getCurrencyCode();
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(PurchaseResponse.from(result, currency));
    }
}
