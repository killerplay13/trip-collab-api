package com.killerplay13.tripcollab.web.dto.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

public record AiItineraryGenerateRequest(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate from,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate to,
    List<String> interests,
    List<String> mustVisitPlaces,
    List<String> avoidPlaces,
    String travelStyle,
    String budgetLevel,
    String notes,
    String language
) {}
