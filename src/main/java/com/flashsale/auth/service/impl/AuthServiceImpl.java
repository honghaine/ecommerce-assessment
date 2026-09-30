package com.flashsale.auth.service.impl;

import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.auth.model.Identifier;
import com.flashsale.auth.model.OtpPurpose;
import com.flashsale.auth.model.TokenPair;
import com.flashsale.auth.service.AuthService;
import com.flashsale.auth.service.OtpService;
import com.flashsale.auth.service.TokenService;
import com.flashsale.auth.support.IdentifierHasher;
import com.flashsale.auth.support.IdentifierParser;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.metrics.BusinessMetrics;
import com.flashsale.common.ratelimit.RateLimiter;
import com.flashsale.notification.service.NotificationService;
import com.flashsale.region.service.RegionService;
import com.flashsale.user.config.WalletProperties;
import com.flashsale.user.entity.User;
import com.flashsale.user.entity.UserStatus;
import com.flashsale.user.entity.Wallet;
import com.flashsale.user.repository.UserRepository;
import com.flashsale.user.repository.WalletRepository;

/**
 * Register / verify / login / refresh / logout.
 *
 * <p>Anti-enumeration: register and resend always answer the same way whether or not
 * the identifier exists; login answers "invalid credentials" for unknown users and wrong
 * passwords alike, and spends the same BCrypt time in both cases.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    static final String OTP_TEMPLATE = "OTP_REGISTER";

    private final IdentifierParser identifierParser;
    private final IdentifierHasher identifierHasher;
    private final RegionService regionService;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final NotificationService notificationService;
    private final TokenService tokenService;
    private final RateLimiter rateLimiter;
    private final WalletProperties walletProperties;
    private final TransactionTemplate transactionTemplate;
    private final String dummyPasswordHash;
    private final BusinessMetrics metrics;

    public AuthServiceImpl(IdentifierParser identifierParser, IdentifierHasher identifierHasher,
                       RegionService regionService, UserRepository users, WalletRepository wallets,
                       PasswordEncoder passwordEncoder, OtpService otpService,
                       NotificationService notificationService, TokenService tokenService,
                       RateLimiter rateLimiter, WalletProperties walletProperties,
                       TransactionTemplate transactionTemplate, BusinessMetrics metrics) {
        this.identifierParser = identifierParser;
        this.identifierHasher = identifierHasher;
        this.regionService = regionService;
        this.users = users;
        this.wallets = wallets;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.notificationService = notificationService;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.walletProperties = walletProperties;
        this.transactionTemplate = transactionTemplate;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
        this.metrics = metrics;
    }

    /**
     * Creates a PENDING buyer + wallet and sends an OTP in one transaction. For an identifier that
     * already exists, a PENDING account gets a fresh OTP and an ACTIVE one is silently ignored.
     */
    @Override
    public void register(String rawIdentifier, String password, String rawRegion, String clientIp) {
        rateLimiter.check("register-ip", clientIp);
        String region = regionService.requireSupported(rawRegion);
        Identifier identifier = identifierParser.parse(rawIdentifier);
        rateLimiter.check("register-identifier", identifierHasher.hash(identifier));
        String passwordHash = passwordEncoder.encode(password);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                Optional<User> existing = findUser(identifier);
                if (existing.isEmpty()) {
                    User user = users.saveAndFlush(User.pendingBuyer(region,
                            identifier.isEmail() ? identifier.value() : null,
                            identifier.isEmail() ? null : identifier.value(),
                            passwordHash));
                    wallets.save(Wallet.open(user, walletProperties.initialBalance()));
                    sendOtp(user, identifier);
                    metrics.registration();
                    log.info("Registered user {} ({})", user.getId(), identifier);
                } else if (existing.get().isPending()) {
                    sendOtp(existing.get(), identifier);
                }
            });
        } catch (DataIntegrityViolationException race) {
            // A concurrent request registered the same identifier first; answer identically.
            log.debug("Concurrent registration for {}", identifier);
        }
    }

    @Override
    public void resendOtp(String rawIdentifier, String clientIp) {
        rateLimiter.check("otp-resend-ip", clientIp);
        Identifier identifier = identifierParser.parse(rawIdentifier);
        transactionTemplate.executeWithoutResult(status ->
                findUser(identifier).filter(User::isPending).ifPresent(user -> sendOtp(user, identifier)));
    }

    @Override
    public void verifyOtp(String rawIdentifier, String code, String clientIp) {
        rateLimiter.check("otp-verify-ip", clientIp);
        Identifier identifier = identifierParser.parse(rawIdentifier);
        if (!otpService.verify(OtpPurpose.REGISTER, identifier, code)) {
            metrics.otp("rejected");
            throw new ApiException(ErrorCode.INVALID_OTP);
        }
        metrics.otp("verified");
        transactionTemplate.executeWithoutResult(status -> {
            User user = findUser(identifier).orElseThrow(() -> new ApiException(ErrorCode.INVALID_OTP));
            user.activate();
            log.info("User {} verified", user.getId());
        });
    }

    @Override
    public TokenPair login(String rawIdentifier, String password, String clientIp) {
        rateLimiter.check("login-ip", clientIp);
        Identifier identifier = identifierParser.parse(rawIdentifier);
        rateLimiter.check("login-identifier", identifierHasher.hash(identifier));

        Optional<User> found = findUser(identifier);
        boolean passwordMatches = passwordEncoder.matches(password,
                found.map(User::getPasswordHash).orElse(dummyPasswordHash));
        if (found.isEmpty() || !passwordMatches) {
            metrics.login("invalid_credentials");
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        // Status is revealed only to someone who already proved the password.
        if (user.getStatus() == UserStatus.PENDING) {
            metrics.login("not_verified");
            throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            metrics.login("locked");
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        metrics.login("success");
        return tokenService.issue(user);
    }

    @Override
    public TokenPair refresh(String refreshToken, String clientIp) {
        rateLimiter.check("refresh-ip", clientIp);
        return tokenService.refresh(refreshToken);
    }

    @Override
    public void logout(Jwt accessToken, String refreshToken) {
        tokenService.revoke(accessToken, refreshToken);
    }

    private Optional<User> findUser(Identifier identifier) {
        return identifier.isEmail() ? users.findByEmail(identifier.value()) : users.findByPhone(identifier.value());
    }

    /** Joins the caller's transaction: the outbox row commits together with the user change. */
    private void sendOtp(User user, Identifier identifier) {
        otpService.issue(OtpPurpose.REGISTER, identifier).ifPresent(code -> {
            metrics.otp("issued");
            notificationService.enqueue(user.getRegion(), identifier.type().channel(), identifier.value(),
                    OTP_TEMPLATE, Map.of("code", code, "expiresInSeconds", otpService.ttlSeconds()));
        });
    }
}
