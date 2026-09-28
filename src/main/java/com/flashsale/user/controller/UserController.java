package com.flashsale.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.common.security.CurrentUser;
import com.flashsale.config.OpenApiConfig;
import com.flashsale.user.dto.MeResponse;
import com.flashsale.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = OpenApiConfig.TAG_USERS)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "Profile of the token owner; email/phone are masked.")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(CurrentUser.from(jwt).id());
    }
}
