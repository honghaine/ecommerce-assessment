package com.flashsale.flashsale.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.flashsale.entity.UserDailyPurchase;
import com.flashsale.flashsale.entity.UserDailyPurchaseId;

public interface UserDailyPurchaseRepository extends JpaRepository<UserDailyPurchase, UserDailyPurchaseId> {
}
