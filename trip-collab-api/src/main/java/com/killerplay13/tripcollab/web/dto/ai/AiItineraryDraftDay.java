package com.killerplay13.tripcollab.web.dto.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

public record AiItineraryDraftDay(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate dayDate,
    List<AiItineraryDraftItem> items
) {}
