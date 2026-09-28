package com.flashsale.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.user.entity.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
}
