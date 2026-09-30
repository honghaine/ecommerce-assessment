package com.flashsale.flashsale.entity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.flashsale.flashsale.support.DaysOfWeek;

/**
 * Platform flash-sale schedule of a region (DB-managed, editable by the platform admin).
 * Default row (migration V4): every day, 60-minute windows → 24 slots per day, 2 days generated ahead.
 */
@Entity
@Table(name = "flash_sale_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashSaleConfig {

    @Id
    private String region;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "active_days", nullable = false)
    private short activeDays;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public int windowsPerDay() {
        return 24 * 60 / slotMinutes;
    }

    public boolean isActiveOn(DayOfWeek day) {
        return DaysOfWeek.contains(activeDays, day);
    }

    /** Whether a local start time falls exactly on a window boundary. */
    public boolean isWindowStart(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && (time.getHour() * 60 + time.getMinute()) % slotMinutes == 0;
    }

    public void update(boolean enabled, int slotMinutes, short activeDays, int horizonDays, long updatedBy) {
        this.enabled = enabled;
        this.slotMinutes = slotMinutes;
        this.activeDays = activeDays;
        this.horizonDays = horizonDays;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
