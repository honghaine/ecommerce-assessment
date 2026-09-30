package com.flashsale.flashsale.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A flash-sale time slot of one region. {@code saleDate} = region-local date of {@code startAt}. */
@Entity
@Table(name = "flash_sale_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashSaleSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SessionType type;

    @Column(name = "seller_id", updatable = false)
    private Long sellerId;

    /** NULL when generated from the region's flash-sale config. */
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(nullable = false)
    private String name;

    @Column(name = "sale_date", nullable = false, updatable = false)
    private LocalDate saleDate;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Platform slot generated from {@code flash_sale_configs}. */
    public static FlashSaleSession generated(String region, String name, LocalDate saleDate, Instant startAt,
                                             Instant endAt) {
        return platformSlot(region, null, name, saleDate, startAt, endAt);
    }

    public static FlashSaleSession platformSlot(String region, Long createdBy, String name, LocalDate saleDate,
                                                Instant startAt, Instant endAt) {
        FlashSaleSession session = new FlashSaleSession();
        session.region = region;
        session.type = SessionType.PLATFORM;
        session.createdBy = createdBy;
        session.name = name;
        session.saleDate = saleDate;
        session.startAt = startAt;
        session.endAt = endAt;
        session.status = SessionStatus.SCHEDULED;
        session.createdAt = Instant.now();
        session.updatedAt = session.createdAt;
        return session;
    }
}
