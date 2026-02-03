package com.killerplay13.tripcollab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "wallet_transactions",
        indexes = {
                @Index(name = "idx_wallet_transactions_wallet_id_created_at", columnList = "wallet_id,created_at")
        }
)
public class WalletTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "txn_type", nullable = false, length = 20)
    private String txnType;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "original_amount", nullable = false, precision = 18, scale = 6)
    private BigDecimal originalAmount;

    @Column(name = "original_currency", nullable = false, length = 3)
    private String originalCurrency;

    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 10)
    private BigDecimal fxRate;

    @Column(name = "computed_base_amount", nullable = false, precision = 18, scale = 6)
    private BigDecimal computedBaseAmount;

    @Column(name = "member_id", columnDefinition = "uuid")
    private UUID memberId;

    @Column(name = "fx_source", length = 50)
    private String fxSource;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "expense_id", columnDefinition = "uuid")
    private UUID expenseId;

    @Column(name = "exchange_group_id", columnDefinition = "uuid")
    private UUID exchangeGroupId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (computedBaseAmount == null) computedBaseAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
