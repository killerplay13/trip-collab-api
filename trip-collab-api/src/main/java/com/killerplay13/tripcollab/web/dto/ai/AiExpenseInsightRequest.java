package com.killerplay13.tripcollab.web.dto.ai;

import java.math.BigDecimal;

public record AiExpenseInsightRequest(
    String language,
    BigDecimal budgetAmount,
    Integer remainingDays
) {}
