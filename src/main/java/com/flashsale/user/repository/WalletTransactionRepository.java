package com.flashsale.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.user.entity.WalletTransaction;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
}
