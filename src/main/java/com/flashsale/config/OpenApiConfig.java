package com.flashsale.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    public static final String TAG_AUTH = "1. Authentication";
    public static final String TAG_USERS = "2. Users";
    public static final String TAG_FLASH_SALE = "3. Flash Sale";
    public static final String TAG_SELLER = "4. Seller";
    public static final String TAG_PLATFORM = "5. Platform admin";
    public static final String TAG_INTEGRATIONS = "6. Integrations";

    private static final String DESCRIPTION = """
            Flash-sale backend (Java 25, Spring Boot 4). All errors are RFC 7807 `application/problem+json` \
            with a stable `code` and a `correlationId`.

            ### Try it in this page
            1. **Register** — `POST /api/v1/auth/register` with an email (or phone like `+84912345678`) and `region` `VN`.
            2. **Get the OTP** — delivery is mocked; read it from the app log:
               `docker compose logs app | grep MOCK`
            3. **Verify** — `POST /api/v1/auth/otp/verify` with the identifier and code.
            4. **Login** — `POST /api/v1/auth/login`, copy `accessToken`.
            5. Click **Authorize** (top right), paste the access token (without `Bearer`).
            6. Call **`GET /api/v1/users/me`**, then **logout** — the same token is rejected afterwards.

            ### Flash sale (demo data is seeded when `SEED_ENABLED=true`, the Docker default)
            1. `GET /api/v1/flash-sales/current?region=VN` — pick an `itemId` from the live slot.
            2. Login as a demo buyer: `buyer0001@demo.flashsale.dev` / `Secret123` (up to `buyer0200`), Authorize.
            3. `POST /api/v1/flash-sales/items/{itemId}/purchase` with a unique `Idempotency-Key`.
            4. Buy again (new key) → `409 ALREADY_PURCHASED_TODAY`; same key → `200` replay.

            ### Configure flash sales
            - Default schedule (DB, per region): **every day, 60-minute windows → 24 slots/day**, 2 days ahead.
            - Seller `seller.vn@flashsale.dev` / `Secret123`: create a product, then a rule
              (`slotStartTime` `12:00`, `daysOfWeek` `["MONDAY","FRIDAY"]`, price, quota) → items appear in those slots.
            - Platform admin `admin.vn@flashsale.dev` / `Secret123`: view/change the schedule, see slots grouped by seller.

            Endpoints without a lock icon are public.
            """;

    @Bean
    OpenAPI flashSaleOpenApi() {
        return new OpenAPI()
                .info(new Info().title("FlashSale Service API").version("v1").description(DESCRIPTION))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .addTagsItem(new Tag().name(TAG_AUTH).description("Register, OTP verification, login, token refresh, logout"))
                .addTagsItem(new Tag().name(TAG_USERS).description("Authenticated user endpoints"))
                .addTagsItem(new Tag().name(TAG_FLASH_SALE).description("Live flash-sale products and purchase"))
                .addTagsItem(new Tag().name(TAG_SELLER).description(
                        "Products, stock and recurring flash-sale rules (slot time × weekdays) — role SELLER"))
                .addTagsItem(new Tag().name(TAG_PLATFORM).description(
                        "Region flash-sale schedule and per-seller slot view — role PLATFORM_ADMIN"))
                .addTagsItem(new Tag().name(TAG_INTEGRATIONS).description(
                        "Machine-to-machine endpoints (API key), e.g. warehouse stock sync"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from `POST /api/v1/auth/login`")));
    }
}
