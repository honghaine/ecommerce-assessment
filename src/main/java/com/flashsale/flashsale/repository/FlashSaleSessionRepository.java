package com.flashsale.flashsale.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.flashsale.entity.FlashSaleSession;
import com.flashsale.flashsale.entity.SessionType;

public interface FlashSaleSessionRepository extends JpaRepository<FlashSaleSession, Long> {

    boolean existsByRegionAndTypeAndStartAt(String region, SessionType type, Instant startAt);
}
