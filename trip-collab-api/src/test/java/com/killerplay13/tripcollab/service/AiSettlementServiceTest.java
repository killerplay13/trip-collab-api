package com.killerplay13.tripcollab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiSettlementServiceTest {

  @Mock
  ExpenseService expenseService;

  @Mock
  TripRepository tripRepository;

  @Test
  void explainReturnsParsedResponseFromFastApi() {
    UUID tripId = UUID.randomUUID();
    UUID aliceId = UUID.randomUUID();
    UUID bobId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, "USD")));
    when(expenseService.summary(tripId)).thenReturn(List.of(
        memberSummary(aliceId, "Alice", "-150.00", "TWD"),
        memberSummary(bobId, "Bob", "150.00", "TWD")
    ));
    when(expenseService.settlements(tripId)).thenReturn(List.of(
        new ExpenseService.SettlementTransfer(
            aliceId,
            "Alice",
            bobId,
            "Bob",
            new BigDecimal("150.00"),
            "TWD"
        )
    ));

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiSettlementService service = service(restClient(builder));

    server.expect(requestTo("http://ai.test/ai/settlement/explain"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.trip_id").value(tripId.toString()))
        .andExpect(jsonPath("$.currency").value("TWD"))
        .andExpect(jsonPath("$.members.length()").value(2))
        .andExpect(jsonPath("$.members[0].member_id").value(aliceId.toString()))
        .andExpect(jsonPath("$.members[0].name").value("Alice"))
        .andExpect(jsonPath("$.balances[0].member_id").value(aliceId.toString()))
        .andExpect(jsonPath("$.balances[0].net_balance").value(-150.0))
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].from").value(aliceId.toString()))
        .andExpect(jsonPath("$.transactions[0].to").value(bobId.toString()))
        .andExpect(jsonPath("$.transactions[0].amount").value(150.0))
        .andRespond(withSuccess("""
            {
              "success": true,
              "data": {
                "summary": "Alice should pay Bob.",
                "steps": ["Alice pays Bob 150 TWD."],
                "tips": ["Use the listed payment to settle."]
              },
              "error": null
            }
            """, MediaType.APPLICATION_JSON));

    var response = service.explain(tripId);

    assertThat(response.tripId()).isEqualTo(tripId);
    assertThat(response.currency()).isEqualTo("TWD");
    assertThat(response.summary()).isEqualTo("Alice should pay Bob.");
    assertThat(response.steps()).containsExactly("Alice pays Bob 150 TWD.");
    assertThat(response.tips()).containsExactly("Use the listed payment to settle.");

    server.verify();
  }

  @Test
  void explainReturns503WhenAiIsDisabled() {
    UUID tripId = UUID.randomUUID();
    AiSettlementService service = new AiSettlementService(
        restClient(RestClient.builder()),
        properties(false),
        expenseService,
        tripRepository
    );

    assertThatThrownBy(() -> service.explain(tripId))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(503);

    verify(tripRepository, never()).findById(tripId);
    verify(expenseService, never()).summary(tripId);
  }

  @Test
  void explainReturns404WhenTripNotFound() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service(restClient(RestClient.builder())).explain(tripId))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);

    verify(expenseService, never()).summary(tripId);
  }

  @Test
  void explainReturnsDefaultWhenSummaryIsEmpty() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, "JPY")));
    when(expenseService.summary(tripId)).thenReturn(List.of());

    var response = service(restClient(RestClient.builder())).explain(tripId);

    assertThat(response.tripId()).isEqualTo(tripId);
    assertThat(response.currency()).isEqualTo("JPY");
    assertThat(response.summary()).isEqualTo("No settlement is needed for this trip yet.");
    assertThat(response.steps()).containsExactly("There are no expenses to explain.");
    assertThat(response.tips()).containsExactly("Add trip expenses first to generate a settlement explanation.");
    verify(expenseService, never()).settlements(tripId);
  }

  @Test
  void explainMapsTimeoutTo504() {
    UUID tripId = UUID.randomUUID();
    UUID aliceId = UUID.randomUUID();
    UUID bobId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, "TWD")));
    when(expenseService.summary(tripId)).thenReturn(List.of(
        memberSummary(aliceId, "Alice", "-150.00", "TWD"),
        memberSummary(bobId, "Bob", "150.00", "TWD")
    ));
    when(expenseService.settlements(tripId)).thenReturn(List.of(
        new ExpenseService.SettlementTransfer(aliceId, "Alice", bobId, "Bob", new BigDecimal("150.00"), "TWD")
    ));

    ClientHttpRequestFactory timeoutFactory = (uri, httpMethod) -> {
      throw new SocketTimeoutException("timed out");
    };
    AiSettlementService service = service(RestClient.builder()
        .baseUrl("http://ai.test")
        .requestFactory(timeoutFactory)
        .build());

    assertThatThrownBy(() -> service.explain(tripId))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(504);
  }

  @Test
  void explainMapsConnectionErrorTo502() {
    UUID tripId = UUID.randomUUID();
    UUID aliceId = UUID.randomUUID();
    UUID bobId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, "TWD")));
    when(expenseService.summary(tripId)).thenReturn(List.of(
        memberSummary(aliceId, "Alice", "-150.00", "TWD"),
        memberSummary(bobId, "Bob", "150.00", "TWD")
    ));
    when(expenseService.settlements(tripId)).thenReturn(List.of(
        new ExpenseService.SettlementTransfer(aliceId, "Alice", bobId, "Bob", new BigDecimal("150.00"), "TWD")
    ));

    ClientHttpRequestFactory connectionErrorFactory = (uri, httpMethod) -> {
      throw new ConnectException("connection refused");
    };
    AiSettlementService service = service(RestClient.builder()
        .baseUrl("http://ai.test")
        .requestFactory(connectionErrorFactory)
        .build());

    assertThatThrownBy(() -> service.explain(tripId))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(502);
  }

  @Test
  void explainMapsFastApiHttpErrorTo502() {
    UUID tripId = UUID.randomUUID();
    stubSettlementInputs(tripId);

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiSettlementService service = service(restClient(builder));

    server.expect(requestTo("http://ai.test/ai/settlement/explain"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> service.explain(tripId))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(502);

    server.verify();
  }

  @Test
  void explainMapsInvalidFastApiResponseTo502() {
    UUID tripId = UUID.randomUUID();
    stubSettlementInputs(tripId);

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiSettlementService service = service(restClient(builder));

    server.expect(requestTo("http://ai.test/ai/settlement/explain"))
        .andRespond(withSuccess("""
            {
              "success": false,
              "data": null,
              "error": {"message": "failed"}
            }
            """, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> service.explain(tripId))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(502);

    server.verify();
  }

  private void stubSettlementInputs(UUID tripId) {
    UUID aliceId = UUID.randomUUID();
    UUID bobId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId, "TWD")));
    when(expenseService.summary(tripId)).thenReturn(List.of(
        memberSummary(aliceId, "Alice", "-150.00", "TWD"),
        memberSummary(bobId, "Bob", "150.00", "TWD")
    ));
    when(expenseService.settlements(tripId)).thenReturn(List.of(
        new ExpenseService.SettlementTransfer(aliceId, "Alice", bobId, "Bob", new BigDecimal("150.00"), "TWD")
    ));
  }

  private AiSettlementService service(RestClient restClient) {
    return new AiSettlementService(restClient, properties(true), expenseService, tripRepository);
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

  private Trip trip(UUID tripId, String currency) {
    Trip trip = new Trip();
    trip.setId(tripId);
    trip.setTitle("Hong Kong family trip");
    trip.setCurrency(currency);
    return trip;
  }

  private ExpenseService.MemberSummary memberSummary(
      UUID memberId,
      String nickname,
      String net,
      String currency
  ) {
    BigDecimal netAmount = new BigDecimal(net);
    return new ExpenseService.MemberSummary(
        memberId,
        nickname,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        netAmount,
        currency
    );
  }
}
