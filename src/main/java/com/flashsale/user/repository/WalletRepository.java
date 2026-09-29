package com.flashsale.user.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.flashsale.user.entity.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /** Atomic conditional debit; 0 rows = insufficient balance. {@code CHECK (balance >= 0)} backs it. */
    @Modifying
    @Query(value = """
            UPDATE wallets
            SET balance = balance - :amount, version = version + 1, updated_at = now()
            WHERE user_id = :userId AND balance >= :amount
            """, nativeQuery = true)
    int debit(long userId, BigDecimal amount);

    @Query("select w.balance from Wallet w where w.userId = :userId")
    BigDecimal findBalance(long userId);
}
