package com.flashsale.outbox.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.security.CurrentUser;
import com.flashsale.config.OpenApiConfig;
import com.flashsale.outbox.dto.OutboxEventView;
import com.flashsale.outbox.entity.OutboxEvent;
import com.flashsale.outbox.entity.OutboxStatus;
import com.flashsale.outbox.repository.OutboxEventRepository;

/** Operational view of the event outbox of the admin's region (dead letters, manual retry). */
@RestController
@RequestMapping("/api/v1/admin/outbox")
@Tag(name = OpenApiConfig.TAG_PLATFORM)
public class OutboxAdminController {

    private final OutboxEventRepository events;

    public OutboxAdminController(OutboxEventRepository events) {
        this.events = events;
    }

    @GetMapping
    @Operation(summary = "List outbox events by status (default FAILED = dead letters)")
    @Transactional(readOnly = true)
    public List<OutboxEventView> list(@RequestParam(defaultValue = "FAILED") OutboxStatus status,
                                      @RequestParam(defaultValue = "50") int limit,
                                      @AuthenticationPrincipal Jwt jwt) {
        return events.findByRegionAndStatusOrderByCreatedAtDesc(CurrentUser.from(jwt).region(), status,
                Limit.of(Math.clamp(limit, 1, 500))).stream().map(OutboxEventView::from).toList();
    }

    @PostMapping("/{eventId}/retry")
    @Operation(summary = "Re-queue a FAILED event (safe: consumers skip effects already applied)")
    @Transactional
    public OutboxEventView retry(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt) {
        OutboxEvent event = events.findById(eventId)
                .filter(e -> e.getRegion().equals(CurrentUser.from(jwt).region()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (event.getStatus() != OutboxStatus.FAILED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        event.requeue();
        return OutboxEventView.from(event);
    }
}
