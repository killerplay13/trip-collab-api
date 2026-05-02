package com.killerplay13.tripcollab.web.dto.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

public record AiItineraryDraftItem(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalTime startTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalTime endTime,
    String title,
    String locationName,
    String mapUrl,
    String note,
    int sortOrder
) {}
