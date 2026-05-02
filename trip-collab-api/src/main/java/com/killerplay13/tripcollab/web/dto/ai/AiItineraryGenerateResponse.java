package com.killerplay13.tripcollab.web.dto.ai;

import java.util.List;
import java.util.UUID;

public record AiItineraryGenerateResponse(
    UUID tripId,
    boolean fallback,
    String fallbackReason,
    String explanation,
    List<String> warnings,
    List<AiItineraryDraftDay> days
) {}
