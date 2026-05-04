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
  private static final String DEFAULT_LANGUAGE = "zh-TW";

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
  public AiSettlementExplainResponse explain(UUID tripId, String language) {
    String effectiveLanguage = normalizeLanguage(language);

    if (!properties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI settlement explanation is disabled");
    }

    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

    List<ExpenseService.MemberSummary> summaries = expenseService.summary(tripId);
    String currency = resolveCurrency(trip, summaries);

    if (summaries.isEmpty()) {
      return emptySettlementResponse(tripId, currency, effectiveLanguage);
    }

    List<ExpenseService.SettlementTransfer> settlements = expenseService.settlements(tripId);
    FastApiSettlementExplainRequest fastApiRequest = toFastApiRequest(
        tripId,
        currency,
        effectiveLanguage,
        summaries,
        settlements
    );
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
      String language,
      List<ExpenseService.MemberSummary> summaries,
      List<ExpenseService.SettlementTransfer> settlements
  ) {
    List<FastApiSettlementMember> members = summaries.stream()
        .map(summary -> new FastApiSettlementMember(
            summary.memberId().toString(),
            summary.nickname()
        ))
        .toList();
    List<FastApiSettlementBalance> balances = summaries.stream()
        .map(summary -> new FastApiSettlementBalance(
            summary.memberId().toString(),
            summary.net().doubleValue()
        ))
        .toList();
    List<FastApiSettlementTransaction> transactions = settlements.stream()
        .map(transfer -> new FastApiSettlementTransaction(
            transfer.fromMemberId().toString(),
            transfer.toMemberId().toString(),
            transfer.amount().doubleValue()
        ))
        .toList();
    List<FastApiSettlementMemberSummary> memberSummaries = summaries.stream()
        .map(summary -> new FastApiSettlementMemberSummary(
            summary.memberId().toString(),
            summary.nickname(),
            summary.paidTotal().doubleValue(),
            summary.owedTotal().doubleValue(),
            summary.net().doubleValue()
        ))
        .toList();
    double totalExpense = summaries.stream()
        .map(ExpenseService.MemberSummary::paidTotal)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
        .doubleValue();

    return new FastApiSettlementExplainRequest(
        tripId.toString(),
        currency,
        language,
        totalExpense,
        summaries.size(),
        settlements.size(),
        members,
        balances,
        transactions,
        memberSummaries
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

  private String normalizeLanguage(String language) {
    return language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.trim();
  }

  private AiSettlementExplainResponse emptySettlementResponse(UUID tripId, String currency, String language) {
    boolean isZh = language != null && language.startsWith("zh");

    if (isZh) {
      return new AiSettlementExplainResponse(
          tripId,
          currency,
          "這次旅程目前尚無需結算的費用。",
          List.of("目前尚無費用記錄。"),
          List.of("請先新增旅程費用，再產生結算說明。")
      );
    }

    return new AiSettlementExplainResponse(
        tripId,
        currency,
        "No settlement is needed for this trip yet.",
        List.of("There are no expenses to explain."),
        List.of("Add trip expenses first to generate a settlement explanation.")
    );
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
      String language,
      @JsonProperty("total_expense") Double totalExpense,
      @JsonProperty("member_count") Integer memberCount,
      @JsonProperty("transaction_count") Integer transactionCount,
      List<FastApiSettlementMember> members,
      List<FastApiSettlementBalance> balances,
      List<FastApiSettlementTransaction> transactions,
      @JsonProperty("member_summaries") List<FastApiSettlementMemberSummary> memberSummaries
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

  private record FastApiSettlementMemberSummary(
      @JsonProperty("member_id") String memberId,
      String name,
      @JsonProperty("paid_total") double paidTotal,
      @JsonProperty("owed_total") double owedTotal,
      @JsonProperty("net_balance") double netBalance
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
