package com.flashsale.catalog.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.catalog.dto.CreateProductRequest;
import com.flashsale.catalog.dto.ProductResponse;
import com.flashsale.catalog.dto.RestockRequest;
import com.flashsale.catalog.dto.UpdateProductRequest;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.catalog.service.ProductService;
import com.flashsale.common.security.CurrentUser;
import com.flashsale.config.OpenApiConfig;

@RestController
@RequestMapping("/api/v1/seller/products")
@Tag(name = OpenApiConfig.TAG_SELLER)
public class SellerProductController {

    private final ProductService productService;

    public SellerProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product with initial stock",
            description = "The product belongs to the calling seller and their region. SKU is unique per region.")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest body, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser seller = CurrentUser.from(jwt);
        return productService.create(seller.id(), seller.region(), body);
    }

    @GetMapping
    @Operation(summary = "List own products with stock (total / available / reserved for flash sales)")
    public List<ProductResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return productService.listOwn(CurrentUser.from(jwt).id());
    }

    @PostMapping("/{productId}/restock")
    @Operation(summary = "Add stock to an own product (idempotent per Idempotency-Key)",
            description = "Retrying with the same `Idempotency-Key` does not add stock twice.")
    public ProductResponse restock(@PathVariable long productId, @Valid @RequestBody RestockRequest body,
                                   @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                   @AuthenticationPrincipal Jwt jwt) {
        if (idempotencyKey == null || !idempotencyKey.matches("^[A-Za-z0-9-]{8,64}$")) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        return productService.restock(CurrentUser.from(jwt).id(), productId, body.quantity(), idempotencyKey);
    }

    @PatchMapping("/{productId}")
    @Operation(summary = "Edit name / description / price / status",
            description = """
                    Price or status changes sync to flash sales: INACTIVE pauses all its rules; a price at or below a \
                    rule's sale price pauses that rule. Paused rules' future occurrences are removed and their \
                    reserved stock returns to available. Purchases of an INACTIVE product are refused immediately.""")
    public ProductResponse update(@PathVariable long productId, @Valid @RequestBody UpdateProductRequest body,
                                  @AuthenticationPrincipal Jwt jwt) {
        return productService.update(CurrentUser.from(jwt).id(), productId, body);
    }
}
