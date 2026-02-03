package com.killerplay13.tripcollab.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
        Long transactionId,
        Long walletId,
        String txnType,
        String direction,
        BigDecimal originalAmount,
        String originalCurrency,
        BigDecimal fxRate,
        BigDecimal computedBaseAmount,
        UUID memberId,
        UUID expenseId,
        UUID exchangeGroupId,
        String fxSource,
        String note,
        Instant createdAt
) {}
