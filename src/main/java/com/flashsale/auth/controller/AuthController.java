package com.flashsale.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.auth.dto.LoginRequest;
import com.flashsale.auth.dto.LogoutRequest;
import com.flashsale.auth.dto.MessageResponse;
import com.flashsale.auth.dto.RefreshRequest;
import com.flashsale.auth.dto.RegisterRequest;
import com.flashsale.auth.dto.ResendOtpRequest;
import com.flashsale.auth.dto.TokenResponse;
import com.flashsale.auth.dto.VerifyOtpRequest;
import com.flashsale.auth.service.AuthService;
import com.flashsale.common.web.ClientIp;
import com.flashsale.config.OpenApiConfig;

/**
 * One endpoint per function; email vs phone is decided from the {@code identifier} value.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = OpenApiConfig.TAG_AUTH)
public class AuthController {

    static final String OTP_SENT = "If the identifier can be registered, a verification code has been sent";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    @Operation(summary = "Register with email or phone",
            description = """
                    Creates an unverified buyer account (+ wallet with demo balance) and sends an OTP \
                    (mocked: see app log). The response is identical whether or not the identifier already exists.""")
    @ApiResponse(responseCode = "202", description = "Accepted — OTP sent if the identifier can be registered")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST / INVALID_IDENTIFIER / UNSUPPORTED_REGION", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public MessageResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        authService.register(body.identifier(), body.password(), body.region(), ClientIp.of(request));
        return new MessageResponse(OTP_SENT);
    }

    @PostMapping("/otp/verify")
    @SecurityRequirements
    @Operation(summary = "Verify OTP", description = "Single-use code, valid 5 minutes, invalidated after 5 wrong attempts.")
    @ApiResponse(responseCode = "200", description = "Account verified")
    @ApiResponse(responseCode = "400", description = "INVALID_OTP / INVALID_REQUEST", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public MessageResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest body, HttpServletRequest request) {
        authService.verifyOtp(body.identifier(), body.code(), ClientIp.of(request));
        return new MessageResponse("Account verified");
    }

    @PostMapping("/otp/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    @Operation(summary = "Resend OTP", description = "Only for unverified accounts; 60 s cooldown. Always answers 202.")
    @ApiResponse(responseCode = "202", description = "Accepted")
    public MessageResponse resendOtp(@Valid @RequestBody ResendOtpRequest body, HttpServletRequest request) {
        authService.resendOtp(body.identifier(), ClientIp.of(request));
        return new MessageResponse(OTP_SENT);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Login with email or phone",
            description = "Returns a 15-min JWT access token and a rotating refresh token.")
    @ApiResponse(responseCode = "200", description = "Tokens issued")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS (same for unknown user and wrong password)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "ACCOUNT_NOT_VERIFIED / ACCOUNT_LOCKED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public TokenResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return TokenResponse.from(authService.login(body.identifier(), body.password(), ClientIp.of(request)));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Refresh tokens",
            description = "Rotates the refresh token. Re-using an already rotated token revokes all sessions of the user.")
    @ApiResponse(responseCode = "200", description = "New token pair")
    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        return TokenResponse.from(authService.refresh(body.refreshToken(), ClientIp.of(request)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Logout",
            description = "Revokes the current access token (until it expires) and, if given, the refresh token.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody(required = false) LogoutRequest body) {
        authService.logout(jwt, body == null ? null : body.refreshToken());
    }
}
