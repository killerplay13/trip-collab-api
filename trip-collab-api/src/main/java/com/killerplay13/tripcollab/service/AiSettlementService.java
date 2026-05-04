package com.killerplay13.tripcollab.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.killerplay13.tripcollab.config.TripCollabAiProperties;
import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.web.dto.ai.AiSettlementExplainResponse;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiSettlementService {
  private static final String DEFAULT_CURRENCY = "TWD";

  private final RestClient restClient;
  private final TripCollabAiProperties properties;
  private final ExpenseService expenseService;
  private final TripRepository tripRepository;

  public AiSettlementService(
      @Qualifier("tripCollabAiRestClient") RestClient restClient,
      TripCollabAiProperties properties,
      ExpenseService expenseService,
      TripRepository tripRepository
  ) {
    this.restClient = restClient;
    this.properties = properties;
    this.expenseService = expenseService;
    this.tripRepository = tripRepository;
  }

  @Transactional(readOnly = true)
  public AiSettlementExplainResponse explain(UUID tripId) {
    if (!properties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI settlement explanation is disabled");
    }

    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

    List<ExpenseService.MemberSummary> summaries = expenseService.summary(tripId);
    String currency = resolveCurrency(trip, summaries);

    if (summaries.isEmpty()) {
      return new AiSettlementExplainResponse(
          tripId,
          currency,
          "No settlement is needed for this trip yet.",
          List.of("There are no expenses to explain."),
          List.of("Add trip expenses first to generate a settlement explanation.")
      );
    }

    List<ExpenseService.SettlementTransfer> settlements = expenseService.settlements(tripId);
    FastApiSettlementExplainRequest fastApiRequest = toFastApiRequest(tripId, currency, summaries, settlements);
    FastApiSettlementApiResponse fastApiResponse = callFastApi(fastApiRequest);
    FastApiSettlementData data = validateResponse(fastApiResponse);

    return new AiSettlementExplainResponse(
        tripId,
        currency,
        data.summary(),
        data.steps() == null ? List.of() : data.steps(),
        data.tips() == null ? List.of() : data.tips()
    );
  }

  private FastApiSettlementExplainRequest toFastApiRequest(
      UUID tripId,
      String currency,
      List<ExpenseService.MemberSummary> summaries,
      List<ExpenseService.SettlementTransfer> settlements
  ) {
    return new FastApiSettlementExplainRequest(
        tripId.toString(),
        currency,
        summaries.stream()
            .map(summary -> new FastApiSettlementMember(
                summary.memberId().toString(),
                summary.nickname()
            ))
            .toList(),
        summaries.stream()
            .map(summary -> new FastApiSettlementBalance(
                summary.memberId().toString(),
                summary.net().doubleValue()
            ))
            .toList(),
        settlements.stream()
            .map(transfer -> new FastApiSettlementTransaction(
                transfer.fromMemberId().toString(),
                transfer.toMemberId().toString(),
                transfer.amount().doubleValue()
            ))
            .toList()
    );
  }

  private FastApiSettlementApiResponse callFastApi(FastApiSettlementExplainRequest request) {
    try {
      return restClient
          .post()
          .uri("/ai/settlement/explain")
          .body(request)
          .retrieve()
          .body(FastApiSettlementApiResponse.class);
    } catch (ResourceAccessException ex) {
      if (isTimeout(ex)) {
        throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI service timeout", ex);
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service connection failed", ex);
    } catch (RestClientResponseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned an error", ex);
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service response is invalid", ex);
    }
  }

  private FastApiSettlementData validateResponse(FastApiSettlementApiResponse response) {
    if (response == null || !response.success() || response.data() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service response is invalid");
    }
    return response.data();
  }

  private String resolveCurrency(Trip trip, List<ExpenseService.MemberSummary> summaries) {
    if (!summaries.isEmpty()) {
      String summaryCurrency = blankToNull(summaries.get(0).currency());
      if (summaryCurrency != null) {
        return summaryCurrency.toUpperCase(Locale.ROOT);
      }
    }

    String tripCurrency = blankToNull(trip.getCurrency());
    if (tripCurrency != null) {
      return tripCurrency.toUpperCase(Locale.ROOT);
    }

    return DEFAULT_CURRENCY;
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean isTimeout(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SocketTimeoutException
          || current.getClass().getName().contains("Timeout")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private record FastApiSettlementExplainRequest(
      @JsonProperty("trip_id") String tripId,
      String currency,
      List<FastApiSettlementMember> members,
      List<FastApiSettlementBalance> balances,
      List<FastApiSettlementTransaction> transactions
  ) {}

  private record FastApiSettlementMember(
      @JsonProperty("member_id") String memberId,
      String name
  ) {}

  private record FastApiSettlementBalance(
      @JsonProperty("member_id") String memberId,
      @JsonProperty("net_balance") double netBalance
  ) {}

  private record FastApiSettlementTransaction(
      @JsonProperty("from") String from,
      String to,
      double amount
  ) {}

  private record FastApiSettlementApiResponse(
      boolean success,
      FastApiSettlementData data,
      Object error
  ) {}

  private record FastApiSettlementData(
      String summary,
      List<String> steps,
      List<String> tips
  ) {}
}
