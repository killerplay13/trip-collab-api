package com.killerplay13.tripcollab.web.dto.ai;

import java.util.List;
import java.util.UUID;

public record AiSettlementExplainResponse(
    UUID tripId,
    String currency,
    String summary,
    List<String> steps,
    List<String> tips
) {}
