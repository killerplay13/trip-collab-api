package com.killerplay13.tripcollab.web.dto.ai;

public record AiItineraryQualityChecks(
    boolean hasOutOfScopePlace,
    boolean hasUnrealisticTransport,
    boolean hasTimeConflict,
    boolean hasDuplicatePlace,
    boolean needsUserReview
) {}
