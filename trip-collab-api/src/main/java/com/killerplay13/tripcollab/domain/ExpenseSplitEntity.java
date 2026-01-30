package com.killerplay13.tripcollab.domain;

import jakarta.persistence.*;
import lombok.*;
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
        name = "expense_splits",
        uniqueConstraints = @UniqueConstraint(name = "ux_splits_expense_member", columnNames = {"expense_id", "member_id"})
)
public class ExpenseSplitEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
