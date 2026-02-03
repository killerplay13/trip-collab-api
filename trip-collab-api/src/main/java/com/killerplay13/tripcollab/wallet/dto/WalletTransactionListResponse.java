package com.killerplay13.tripcollab.wallet.dto;

import java.util.List;

public record WalletTransactionListResponse(
        List<WalletTransactionResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
