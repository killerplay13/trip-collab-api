package com.killerplay13.tripcollab.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killerplay13.tripcollab.service.AiSettlementService;
import com.killerplay13.tripcollab.web.dto.ai.AiSettlementExplainResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiSettlementControllerTest {

  @Test
  void explainCallsServiceAndReturnsResponse() throws Exception {
    UUID tripId = UUID.randomUUID();
    AiSettlementService service = mock(AiSettlementService.class);
    MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new AiSettlementController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    when(service.explain(eq(tripId), eq("zh-TW"))).thenReturn(response(tripId));

    mvc.perform(post("/api/trips/{tripId}/expenses/ai/explain", tripId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tripId").value(tripId.toString()))
        .andExpect(jsonPath("$.currency").value("TWD"))
        .andExpect(jsonPath("$.summary").value("Alice should pay Bob."))
        .andExpect(jsonPath("$.steps[0]").value("Alice pays Bob 150 TWD."))
        .andExpect(jsonPath("$.tips[0]").value("Use the listed payment to settle."));

    verify(service).explain(tripId, "zh-TW");
  }

  @Test
  void explainWithLanguageParamPassesLanguageToService() throws Exception {
    UUID tripId = UUID.randomUUID();
    AiSettlementService service = mock(AiSettlementService.class);
    MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new AiSettlementController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    when(service.explain(eq(tripId), eq("zh-TW"))).thenReturn(response(tripId));

    mvc.perform(post("/api/trips/{tripId}/expenses/ai/explain?language=zh-TW", tripId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tripId").value(tripId.toString()))
        .andExpect(jsonPath("$.currency").value("TWD"))
        .andExpect(jsonPath("$.summary").value("Alice should pay Bob."))
        .andExpect(jsonPath("$.steps[0]").value("Alice pays Bob 150 TWD."))
        .andExpect(jsonPath("$.tips[0]").value("Use the listed payment to settle."));

    verify(service).explain(tripId, "zh-TW");
  }

  @Test
  void explainWithEnLanguagePassesEnToService() throws Exception {
    UUID tripId = UUID.randomUUID();
    AiSettlementService service = mock(AiSettlementService.class);
    MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new AiSettlementController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    when(service.explain(eq(tripId), eq("en"))).thenReturn(response(tripId));

    mvc.perform(post("/api/trips/{tripId}/expenses/ai/explain?language=en", tripId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tripId").value(tripId.toString()))
        .andExpect(jsonPath("$.currency").value("TWD"))
        .andExpect(jsonPath("$.summary").value("Alice should pay Bob."))
        .andExpect(jsonPath("$.steps[0]").value("Alice pays Bob 150 TWD."))
        .andExpect(jsonPath("$.tips[0]").value("Use the listed payment to settle."));

    verify(service).explain(tripId, "en");
  }

  private AiSettlementExplainResponse response(UUID tripId) {
    return new AiSettlementExplainResponse(
        tripId,
        "TWD",
        "Alice should pay Bob.",
        List.of("Alice pays Bob 150 TWD."),
        List.of("Use the listed payment to settle.")
    );
  }
}
