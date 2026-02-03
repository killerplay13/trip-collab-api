package com.killerplay13.tripcollab.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletDepositRequest(
        UUID memberId,
        BigDecimal originalAmount,
        String originalCurrency,
        BigDecimal fxRate,
        String fxSource,
        String note
) {}
