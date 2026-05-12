package com.killerplay13.tripcollab.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killerplay13.tripcollab.service.AiExpenseInsightService;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightRequest;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiExpenseInsightControllerTest {

  @Test
  void insightPathCallsServiceAndReturnsResponse() throws Exception {
    UUID tripId = UUID.randomUUID();
    AiExpenseInsightService service = mock(AiExpenseInsightService.class);
    MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new AiExpenseInsightController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    when(service.insight(eq(tripId), any(AiExpenseInsightRequest.class)))
        .thenReturn(new AiExpenseInsightResponse(
            "Trip expenses total 8000 TWD.",
            List.of("Hotel is the largest expense."),
            List.of(),
            List.of("Keep daily spend under 6000 TWD."),
            false,
            null
        ));

    mvc.perform(post("/api/trips/{tripId}/expenses/ai/insight", tripId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "language": "zh-TW",
                  "budgetAmount": 20000,
                  "remainingDays": 2
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary").value("Trip expenses total 8000 TWD."))
        .andExpect(jsonPath("$.highlights[0]").value("Hotel is the largest expense."))
        .andExpect(jsonPath("$.suggestions[0]").value("Keep daily spend under 6000 TWD."))
        .andExpect(jsonPath("$.fallback").value(false));

    ArgumentCaptor<AiExpenseInsightRequest> requestCaptor = ArgumentCaptor.forClass(AiExpenseInsightRequest.class);
    verify(service).insight(eq(tripId), requestCaptor.capture());
    AiExpenseInsightRequest request = requestCaptor.getValue();
    org.assertj.core.api.Assertions.assertThat(request.language()).isEqualTo("zh-TW");
    org.assertj.core.api.Assertions.assertThat(request.budgetAmount()).isEqualByComparingTo(new BigDecimal("20000"));
    org.assertj.core.api.Assertions.assertThat(request.remainingDays()).isEqualTo(2);
  }
}
