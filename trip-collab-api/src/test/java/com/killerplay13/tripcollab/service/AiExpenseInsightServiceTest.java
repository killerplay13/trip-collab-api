package com.killerplay13.tripcollab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.killerplay13.tripcollab.config.TripCollabAiProperties;
import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightRequest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class AiExpenseInsightServiceTest {

  @Mock
  TripRepository tripRepository;

  @Test
  void insightReturnsParsedResponseFromFastApi() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId)));

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiExpenseInsightService service = service(restClient(builder), true);

    server.expect(requestTo("http://ai.test/ai/expenses/insight"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.trip_id").value(tripId.toString()))
        .andExpect(jsonPath("$.language").value("zh-TW"))
        .andExpect(jsonPath("$.budget_amount").value(20000))
        .andExpect(jsonPath("$.remaining_days").value(2))
        .andRespond(withSuccess("""
            {
              "success": true,
              "data": {
                "summary": "mock summary",
                "highlights": ["flow ok"],
                "warnings": ["mock only"],
                "suggestions": ["use real context next"],
                "fallback": false,
                "fallbackReason": null
              },
              "error": null
            }
            """, MediaType.APPLICATION_JSON));

    var response = service.insight(
        tripId,
        new AiExpenseInsightRequest("zh-TW", new BigDecimal("20000"), 2)
    );

    assertThat(response.summary()).isEqualTo("mock summary");
    assertThat(response.highlights()).containsExactly("flow ok");
    assertThat(response.warnings()).containsExactly("mock only");
    assertThat(response.suggestions()).containsExactly("use real context next");
    assertThat(response.fallback()).isFalse();
    assertThat(response.fallbackReason()).isNull();

    server.verify();
  }

  @Test
  void insightReturnsFallbackWhenAiIsDisabled() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId)));

    AiExpenseInsightService service = service(restClient(RestClient.builder()), false);

    var response = service.insight(tripId, new AiExpenseInsightRequest("en", null, null));

    assertThat(response.fallback()).isTrue();
    assertThat(response.fallbackReason()).isEqualTo("disabled");
    assertThat(response.summary()).isNotBlank();
    assertThat(response.highlights()).isNotEmpty();
  }

  @Test
  void insightReturnsFallbackWhenFastApiFails() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId)));

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiExpenseInsightService service = service(restClient(builder), true);

    server.expect(requestTo("http://ai.test/ai/expenses/insight"))
        .andRespond(withServerError());

    var response = service.insight(tripId, new AiExpenseInsightRequest("en", null, null));

    assertThat(response.fallback()).isTrue();
    assertThat(response.fallbackReason()).isEqualTo("fastapi_error");

    server.verify();
  }

  private AiExpenseInsightService service(RestClient restClient, boolean enabled) {
    return new AiExpenseInsightService(restClient, properties(enabled), tripRepository);
  }

  private RestClient restClient(RestClient.Builder builder) {
    return builder.baseUrl("http://ai.test").build();
  }

  private TripCollabAiProperties properties(boolean enabled) {
    TripCollabAiProperties properties = new TripCollabAiProperties();
    properties.setEnabled(enabled);
    properties.setBaseUrl("http://ai.test");
    properties.setTimeoutSeconds(10);
    return properties;
  }

  private Trip trip(UUID tripId) {
    Trip trip = new Trip();
    trip.setId(tripId);
    trip.setTitle("Hong Kong family trip");
    trip.setCurrency("TWD");
    return trip;
  }
}
