package com.flashsale.inventory.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.config.OpenApiConfig;
import com.flashsale.inventory.config.WarehouseProperties;
import com.flashsale.inventory.dto.WarehouseSyncRequest;
import com.flashsale.inventory.dto.WarehouseSyncResponse;
import com.flashsale.inventory.service.WarehouseSyncService;

/** Machine-to-machine endpoint for the external warehouse system; authenticated by API key, not user JWT. */
@RestController
@RequestMapping("/api/v1/integrations/warehouse")
@Tag(name = OpenApiConfig.TAG_INTEGRATIONS)
public class WarehouseSyncController {

    private final WarehouseSyncService syncService;
    private final WarehouseProperties properties;

    public WarehouseSyncController(WarehouseSyncService syncService, WarehouseProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @PostMapping("/stock-events")
    @SecurityRequirements
    @Operation(summary = "Push stock deltas from the warehouse (idempotent per source + eventId)",
            description = """
                    Header `X-Api-Key` = `WAREHOUSE_API_KEY`. Each event is applied at most once; re-sending returns \\
                    DUPLICATE with the original outcome. Deltas only change sellable stock (`available` and `total`); \\
                    flash-sale reservations are never touched.""")
    public WarehouseSyncResponse stockEvents(
            @Parameter(description = "Warehouse integration key") @RequestHeader(name = "X-Api-Key", required = false)
            String apiKey,
            @Valid @RequestBody WarehouseSyncRequest body) {
        requireValidKey(apiKey);
        return syncService.apply(body);
    }

    private void requireValidKey(String presented) {
        String expected = properties.apiKey();
        if (expected == null || expected.isBlank() || presented == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }
}
