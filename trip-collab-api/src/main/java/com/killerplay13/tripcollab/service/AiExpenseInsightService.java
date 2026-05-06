package com.killerplay13.tripcollab.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.killerplay13.tripcollab.config.TripCollabAiProperties;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightRequest;
import com.killerplay13.tripcollab.web.dto.ai.AiExpenseInsightResponse;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
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
public class AiExpenseInsightService {
  private static final String DEFAULT_LANGUAGE = "zh-TW";

  private final RestClient restClient;
  private final TripCollabAiProperties properties;
  private final TripRepository tripRepository;

  public AiExpenseInsightService(
      @Qualifier("tripCollabAiRestClient") RestClient restClient,
      TripCollabAiProperties properties,
      TripRepository tripRepository
  ) {
    this.restClient = restClient;
    this.properties = properties;
    this.tripRepository = tripRepository;
  }

  @Transactional(readOnly = true)
  public AiExpenseInsightResponse insight(UUID tripId, AiExpenseInsightRequest request) {
    tripRepository.findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

    String language = normalizeLanguage(request == null ? null : request.language());

    if (!properties.isEnabled()) {
      return fallback(language, "disabled");
    }

    FastApiExpenseInsightRequest fastApiRequest = new FastApiExpenseInsightRequest(
        tripId.toString(),
        language,
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
          "AI 花費分析暫時無法使用，目前顯示安全 fallback 摘要。",
          List.of("AI 花費分析入口已透過 Spring Boot 建立。"),
          List.of("這是 fallback 結果，因為 AI service 未完成請求。"),
          List.of("請稍後再試，或先使用既有支出列表與分帳功能。"),
          true,
          reason
      );
    }

    return new AiExpenseInsightResponse(
        "AI expense insight is temporarily unavailable. Showing a safe fallback summary.",
        List.of("Expense insight flow is available through Spring Boot."),
        List.of("This fallback was generated because the AI service could not complete the request."),
        List.of("Please try again later, or continue using the existing expense and settlement views."),
        true,
        reason
    );
  }

  private String normalizeLanguage(String language) {
    return language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.trim();
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
      @JsonProperty("budget_amount") BigDecimal budgetAmount,
      @JsonProperty("remaining_days") Integer remainingDays
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
