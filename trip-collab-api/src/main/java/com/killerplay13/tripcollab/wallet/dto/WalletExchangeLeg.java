package com.killerplay13.tripcollab.wallet.dto;

import java.math.BigDecimal;

public record WalletExchangeLeg(
        String currency,
        BigDecimal amount,
        BigDecimal fxRateToBase
) {}
