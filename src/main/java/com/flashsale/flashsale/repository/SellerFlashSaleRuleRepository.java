package com.flashsale.flashsale.repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.flashsale.entity.RuleStatus;
import com.flashsale.flashsale.entity.SellerFlashSaleRule;

public interface SellerFlashSaleRuleRepository extends JpaRepository<SellerFlashSaleRule, Long> {

    List<SellerFlashSaleRule> findByRegionAndStatus(String region, RuleStatus status);

    List<SellerFlashSaleRule> findByProductIdAndStatus(long productId, RuleStatus status);

    List<SellerFlashSaleRule> findBySellerIdAndStatusNotOrderBySlotStartTimeAscIdAsc(long sellerId, RuleStatus status);

    Optional<SellerFlashSaleRule> findByIdAndSellerId(long id, long sellerId);

    boolean existsByProductIdAndSlotStartTimeAndStatusNot(long productId, LocalTime slotStartTime, RuleStatus status);

    boolean existsByProductIdAndSlotStartTimeAndStatusNotAndIdNot(long productId, LocalTime slotStartTime,
                                                                   RuleStatus status, long id);
}
