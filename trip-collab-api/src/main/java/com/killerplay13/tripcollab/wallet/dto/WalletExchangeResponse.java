package com.killerplay13.tripcollab.wallet.dto;

import java.util.List;
import java.util.UUID;

public record WalletExchangeResponse(
        UUID exchangeGroupId,
        Long walletId,
        List<WalletTransactionResponse> transactions
) {}
