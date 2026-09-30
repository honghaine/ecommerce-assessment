package com.flashsale.flashsale.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.flashsale.flashsale.entity.FlashSaleSession;
import com.flashsale.flashsale.entity.SessionType;

public interface FlashSaleSessionRepository extends JpaRepository<FlashSaleSession, Long> {

    boolean existsByRegionAndTypeAndStartAt(String region, SessionType type, Instant startAt);

    List<FlashSaleSession> findByRegionAndSaleDateOrderByStartAt(String region, LocalDate saleDate);

    Optional<FlashSaleSession> findByIdAndRegion(long id, String region);

    /** Sessions of the region overlapping [from, to). */
    @Query("""
            select s from FlashSaleSession s
            where s.region = :region and s.startAt < :to and s.endAt > :from
            order by s.startAt
            """)
    List<FlashSaleSession> findOverlapping(String region, Instant from, Instant to);
}
