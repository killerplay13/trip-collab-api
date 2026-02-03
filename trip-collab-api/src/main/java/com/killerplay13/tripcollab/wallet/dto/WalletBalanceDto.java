package com.killerplay13.tripcollab.wallet.dto;

import java.math.BigDecimal;

public record WalletBalanceDto(
        String currency,
        BigDecimal balance
) {}
