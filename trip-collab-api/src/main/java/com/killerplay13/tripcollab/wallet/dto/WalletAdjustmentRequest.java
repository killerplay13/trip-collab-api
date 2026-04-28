package com.killerplay13.tripcollab.wallet.dto;

import java.math.BigDecimal;

public record WalletAdjustmentRequest(
        String direction,
        BigDecimal originalAmount,
        String originalCurrency,
        BigDecimal fxRate,
        String fxSource,
        String note
) {}
