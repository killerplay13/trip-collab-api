package com.killerplay13.tripcollab.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killerplay13.tripcollab.service.AiItineraryService;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryDraftDay;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryDraftItem;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryGenerateRequest;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryGenerateResponse;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryQualityChecks;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiItineraryControllerTest {

  @Test
  void generatePathCallsService() throws Exception {
    UUID tripId = UUID.randomUUID();
    AiItineraryService service = mock(AiItineraryService.class);
    MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new AiItineraryController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    when(service.generate(eq(tripId), any(AiItineraryGenerateRequest.class)))
        .thenReturn(new AiItineraryGenerateResponse(
            tripId,
            false,
            null,
            "Generated draft.",
            List.of(),
            new AiItineraryQualityChecks(false, false, false, false, false),
            List.of(new AiItineraryDraftDay(
                LocalDate.of(2026, 5, 1),
                List.of(new AiItineraryDraftItem(
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0),
                    "Central food walk",
                    "Central",
                    null,
                    "Easy pace",
                    1
                ))
            ))
        ));

    mvc.perform(post("/api/trips/{tripId}/itinerary/ai/generate", tripId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "from": "2026-05-01",
                  "to": "2026-05-03",
                  "interests": ["food"],
                  "mustVisitPlaces": [],
                  "avoidPlaces": [],
                  "travelStyle": "food",
                  "budgetLevel": "medium",
                  "notes": "Prefer transit",
                  "language": "zh-TW"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tripId").value(tripId.toString()))
        .andExpect(jsonPath("$.fallback").value(false))
        .andExpect(jsonPath("$.days[0].dayDate").value("2026-05-01"))
        .andExpect(jsonPath("$.days[0].items[0].title").value("Central food walk"));

    verify(service).generate(eq(tripId), any(AiItineraryGenerateRequest.class));
  }
}
