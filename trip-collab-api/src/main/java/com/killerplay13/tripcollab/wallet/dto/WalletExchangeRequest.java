package com.killerplay13.tripcollab.wallet.dto;

import java.util.UUID;

public record WalletExchangeRequest(
        UUID memberId,
        WalletExchangeLeg from,
        WalletExchangeLeg to,
        String fxSource,
        String note
) {}
