package com.killerplay13.tripcollab.wallet.dto;

import java.math.BigDecimal;

public record TotalsInBaseDto(
        BigDecimal depositsIn,
        BigDecimal withdrawalsOut,
        BigDecimal spentOut,
        BigDecimal adjustmentsNet
) {}
