package com.killerplay13.tripcollab.wallet.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WalletSummaryResponse(
        Long walletId,
        UUID tripId,
        String baseCurrency,
        List<WalletBalanceDto> balances,
        TotalsInBaseDto totalsInBase,
        Instant updatedAt
) {}
