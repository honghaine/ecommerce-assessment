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

            Endpoints without a lock icon are public.
            """;

    @Bean
    OpenAPI flashSaleOpenApi() {
        return new OpenAPI()
                .info(new Info().title("FlashSale Service API").version("v1").description(DESCRIPTION))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .addTagsItem(new Tag().name(TAG_AUTH).description("Register, OTP verification, login, token refresh, logout"))
                .addTagsItem(new Tag().name(TAG_USERS).description("Authenticated user endpoints"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from `POST /api/v1/auth/login`")));
    }
}
