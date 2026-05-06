package com.killerplay13.tripcollab.web.dto.ai;

import java.util.List;

public record AiExpenseInsightResponse(
    String summary,
    List<String> highlights,
    List<String> warnings,
    List<String> suggestions,
    boolean fallback,
    String fallbackReason
) {}
