package com.killerplay13.tripcollab.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.killerplay13.tripcollab.config.TripCollabAiProperties;
import com.killerplay13.tripcollab.domain.ExpenseEntity;
import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightRequest;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
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
public class AiExpenseInsightService {
  private static final String DEFAULT_LANGUAGE = "zh-TW";

  private final RestClient restClient;
  private final TripCollabAiProperties properties;
  private final TripRepository tripRepository;
  private final ExpenseService expenseService;

  public AiExpenseInsightService(
      @Qualifier("tripCollabAiRestClient") RestClient restClient,
      TripCollabAiProperties properties,
      TripRepository tripRepository,
      ExpenseService expenseService
  ) {
    this.restClient = restClient;
    this.properties = properties;
    this.tripRepository = tripRepository;
    this.expenseService = expenseService;
  }

  @Transactional(readOnly = true)
  public AiExpenseInsightResponse insight(UUID tripId, AiExpenseInsightRequest request) {
    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

    String language = normalizeLanguage(request == null ? null : request.language());
    List<ExpenseEntity> expenses = expenseService.listAll(tripId);
    List<ExpenseService.MemberSummary> memberSummaries = expenseService.summary(tripId);

    if (!properties.isEnabled()) {
      return fallback(language, "disabled");
    }

    FastApiExpenseInsightRequest fastApiRequest = new FastApiExpenseInsightRequest(
        tripId.toString(),
        language,
        resolveCurrency(trip, memberSummaries),
        totalAmount(expenses),
        expenses.size(),
        memberSummaries.size(),
        dailyTotals(expenses),
        topExpenses(expenses),
        memberBalances(memberSummaries),
        request == null ? null : request.budgetAmount(),
        request == null ? null : request.remainingDays()
    );

    try {
      FastApiExpenseInsightApiResponse fastApiResponse = callFastApi(fastApiRequest);
      FastApiExpenseInsightData data = validateResponse(fastApiResponse);
      return new AiExpenseInsightResponse(
          data.summary(),
          data.highlights() == null ? List.of() : data.highlights(),
          data.warnings() == null ? List.of() : data.warnings(),
          data.suggestions() == null ? List.of() : data.suggestions(),
          data.fallback(),
          data.fallbackReason()
      );
    } catch (ResponseStatusException ex) {
      return fallback(language, ex.getReason() == null ? "ai_service_error" : ex.getReason());
    }
  }

  private FastApiExpenseInsightApiResponse callFastApi(FastApiExpenseInsightRequest request) {
    try {
      return restClient
          .post()
          .uri("/ai/expenses/insight")
          .body(request)
          .retrieve()
          .body(FastApiExpenseInsightApiResponse.class);
    } catch (ResourceAccessException ex) {
      if (isTimeout(ex)) {
        throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "timeout", ex);
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "connection_failed", ex);
    } catch (RestClientResponseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "fastapi_error", ex);
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "invalid_response", ex);
    }
  }

  private FastApiExpenseInsightData validateResponse(FastApiExpenseInsightApiResponse response) {
    if (response == null || !response.success() || response.data() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "invalid_response");
    }
    return response.data();
  }

  private AiExpenseInsightResponse fallback(String language, String reason) {
    if (language != null && language.startsWith("zh")) {
      return new AiExpenseInsightResponse(
          "AI 花費分析暫時無法使用，請稍後再試。",
          List.of(),
          List.of("AI 花費分析暫時無法使用，請稍後再試。"),
          List.of(),
          true,
          reason
      );
    }

    return new AiExpenseInsightResponse(
        "AI expense insight is temporarily unavailable. Please try again later.",
        List.of(),
        List.of("AI expense insight is temporarily unavailable. Please try again later."),
        List.of(),
        true,
        reason
    );
  }

  private String normalizeLanguage(String language) {
    return language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.trim();
  }

  private String resolveCurrency(Trip trip, List<ExpenseService.MemberSummary> memberSummaries) {
    if (trip.getCurrency() != null && !trip.getCurrency().isBlank()) {
      return trip.getCurrency().trim().toUpperCase(Locale.ROOT);
    }
    return memberSummaries.stream()
        .map(ExpenseService.MemberSummary::currency)
        .filter(currency -> currency != null && !currency.isBlank())
        .findFirst()
        .map(currency -> currency.trim().toUpperCase(Locale.ROOT))
        .orElse("TWD");
  }

  private BigDecimal totalAmount(List<ExpenseEntity> expenses) {
    return expenses.stream()
        .map(ExpenseEntity::getAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private List<FastApiDailyTotal> dailyTotals(List<ExpenseEntity> expenses) {
    Map<LocalDate, BigDecimal> totals = expenses.stream()
        .filter(expense -> expense.getExpenseDate() != null)
        .collect(Collectors.groupingBy(
            ExpenseEntity::getExpenseDate,
            TreeMap::new,
            Collectors.mapping(
                expense -> expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount(),
                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
            )
        ));

    return totals.entrySet().stream()
        .map(entry -> new FastApiDailyTotal(entry.getKey().toString(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
        .toList();
  }

  private List<FastApiTopExpense> topExpenses(List<ExpenseEntity> expenses) {
    return expenses.stream()
        .sorted(Comparator
            .comparing((ExpenseEntity expense) -> expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount())
            .reversed())
        .limit(5)
        .map(expense -> new FastApiTopExpense(
            expense.getTitle(),
            (expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount()).setScale(2, RoundingMode.HALF_UP),
            expense.getExpenseDate() == null ? null : expense.getExpenseDate().toString()
        ))
        .toList();
  }

  private List<FastApiMemberBalance> memberBalances(List<ExpenseService.MemberSummary> summaries) {
    return summaries.stream()
        .map(summary -> new FastApiMemberBalance(
            summary.nickname(),
            summary.paidTotal().setScale(2, RoundingMode.HALF_UP),
            summary.owedTotal().setScale(2, RoundingMode.HALF_UP),
            summary.net().setScale(2, RoundingMode.HALF_UP)
        ))
        .toList();
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

  private record FastApiExpenseInsightRequest(
      @JsonProperty("trip_id") String tripId,
      String language,
      String currency,
      @JsonProperty("totalAmount") BigDecimal totalAmount,
      @JsonProperty("expenseCount") int expenseCount,
      @JsonProperty("memberCount") int memberCount,
      @JsonProperty("dailyTotals") List<FastApiDailyTotal> dailyTotals,
      @JsonProperty("topExpenses") List<FastApiTopExpense> topExpenses,
      @JsonProperty("memberBalances") List<FastApiMemberBalance> memberBalances,
      @JsonProperty("budgetAmount") BigDecimal budgetAmount,
      @JsonProperty("remainingDays") Integer remainingDays
  ) {}

  private record FastApiDailyTotal(
      String date,
      BigDecimal amount
  ) {}

  private record FastApiTopExpense(
      String title,
      BigDecimal amount,
      String date
  ) {}

  private record FastApiMemberBalance(
      @JsonProperty("memberName") String memberName,
      @JsonProperty("paidAmount") BigDecimal paidAmount,
      @JsonProperty("shareAmount") BigDecimal shareAmount,
      BigDecimal balance
  ) {}

  private record FastApiExpenseInsightApiResponse(
      boolean success,
      FastApiExpenseInsightData data,
      Object error
  ) {}

  private record FastApiExpenseInsightData(
      String summary,
      List<String> highlights,
      List<String> warnings,
      List<String> suggestions,
      boolean fallback,
      String fallbackReason
  ) {}
}
