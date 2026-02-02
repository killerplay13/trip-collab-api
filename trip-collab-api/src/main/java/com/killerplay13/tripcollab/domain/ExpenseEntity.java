package com.killerplay13.tripcollab.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "expenses")
public class ExpenseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "TWD";

    @Column(name = "paid_by_member_id", nullable = false)
    private UUID paidByMemberId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "note")
    private String note;

    @Column(name = "created_by_member_id")
    private UUID createdByMemberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (expenseDate == null) expenseDate = LocalDate.now();
        if (currency == null || currency.isBlank()) currency = "TWD";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    @Column(name = "original_amount", precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "original_currency", length = 3)
    private String originalCurrency;

    @Column(name = "fx_rate", precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "fx_source", length = 20)
    private String fxSource;

    @Column(name = "amount_overridden", nullable = false)
    private Boolean amountOverridden = false;

}
