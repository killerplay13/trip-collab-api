package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.WalletTransactionEntity;
import com.killerplay13.tripcollab.wallet.dto.TotalsInBaseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, Long> {

    @Query("""
        select new com.killerplay13.tripcollab.wallet.dto.TotalsInBaseDto(
            coalesce(sum(case when t.txnType = 'DEPOSIT' and t.direction = 'IN' then t.computedBaseAmount else 0 end), 0),
            coalesce(sum(case when t.txnType = 'WITHDRAW' and t.direction = 'OUT' then t.computedBaseAmount else 0 end), 0),
            coalesce(sum(case when t.txnType = 'EXPENSE' and t.direction = 'OUT' then t.computedBaseAmount else 0 end), 0),
            coalesce(sum(case
                when t.txnType = 'ADJUSTMENT' and t.direction = 'IN' then t.computedBaseAmount
                when t.txnType = 'ADJUSTMENT' and t.direction = 'OUT' then -t.computedBaseAmount
                else 0 end), 0)
        )
        from WalletTransactionEntity t
        where t.walletId = :walletId
    """)
    TotalsInBaseDto aggregateTotalsInBase(@Param("walletId") Long walletId);

    @Query("""
        select t
        from WalletTransactionEntity t
        where t.walletId = :walletId
          and (:currency is null or t.originalCurrency = :currency)
          and (:txnType is null or t.txnType = :txnType)
          and (:exchangeGroupId is null or t.exchangeGroupId = :exchangeGroupId)
        order by t.createdAt desc, t.id desc
    """)
    Page<WalletTransactionEntity> search(
            @Param("walletId") Long walletId,
            @Param("currency") String currency,
            @Param("txnType") String txnType,
            @Param("exchangeGroupId") UUID exchangeGroupId,
            Pageable pageable
    );

    Optional<WalletTransactionEntity> findByIdAndWalletId(Long id, Long walletId);

    boolean existsByExpenseIdAndTxnType(UUID expenseId, String txnType);
}
