package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.WalletBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface WalletBalanceRepository extends JpaRepository<WalletBalanceEntity, Long> {
    List<WalletBalanceEntity> findAllByWalletIdOrderByCurrencyAsc(Long walletId);
    Optional<WalletBalanceEntity> findByWalletIdAndCurrency(Long walletId, String currency);

    @Modifying
    @Query(value = """
        INSERT INTO wallet_balances (wallet_id, currency, balance, created_at, updated_at)
        VALUES (:walletId, :currency, :delta, NOW(), NOW())
        ON CONFLICT (wallet_id, currency)
        DO UPDATE SET
          balance = wallet_balances.balance + EXCLUDED.balance,
          updated_at = NOW()
        """, nativeQuery = true)
    void upsertBalance(
            @Param("walletId") Long walletId,
            @Param("currency") String currency,
            @Param("delta") BigDecimal delta
    );

    @Modifying
    @Query(value = """
        UPDATE wallet_balances
        SET balance = balance - :amount,
            updated_at = NOW()
        WHERE wallet_id = :walletId
          AND currency = :currency
          AND balance >= :amount
        """, nativeQuery = true)
    int debitIfSufficient(
            @Param("walletId") Long walletId,
            @Param("currency") String currency,
            @Param("amount") BigDecimal amount
    );
}
