package com.killerplay13.tripcollab.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.killerplay13.tripcollab.config.TripCollabAiProperties;
import com.killerplay13.tripcollab.domain.ItineraryItem;
import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.repo.ItineraryItemRepository;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryDraftDay;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryDraftItem;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryGenerateRequest;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryGenerateResponse;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
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
public class AiItineraryService {
  private static final String DEFAULT_LANGUAGE = "zh-TW";
  private static final int MAX_EXISTING_ITINERARY_ITEMS = 100;
  private static final int MAX_EXISTING_NOTE_LENGTH = 120;

  private final TripCollabAiProperties properties;
  private final TripRepository tripRepository;
  private final TripMemberRepository tripMemberRepository;
  private final ItineraryItemRepository itineraryItemRepository;
  private final RestClient restClient;

  public AiItineraryService(
      TripCollabAiProperties properties,
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      ItineraryItemRepository itineraryItemRepository,
      @Qualifier("tripCollabAiRestClient") RestClient restClient
  ) {
    this.properties = properties;
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.itineraryItemRepository = itineraryItemRepository;
    this.restClient = restClient;
  }

  @Transactional(readOnly = true)
  public AiItineraryGenerateResponse generate(UUID tripId, AiItineraryGenerateRequest request) {
    if (!properties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI itinerary generation is disabled");
    }
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
    }

    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

    List<FastApiExistingItineraryItem> existingItinerary = existingItineraryContext(trip.getId());
    FastApiItineraryGenerateRequest fastApiRequest = toFastApiRequest(trip, request, existingItinerary);
    FastApiResponse fastApiResponse = callFastApi(fastApiRequest);
    FastApiItineraryGenerateData data = validateResponse(fastApiResponse);

    return new AiItineraryGenerateResponse(
        tripId,
        data.fallback(),
        data.fallbackReason(),
        data.explanation(),
        data.warnings() == null ? List.of() : data.warnings(),
        groupDays(data.items())
    );
  }

  private FastApiItineraryGenerateRequest toFastApiRequest(
      Trip trip,
      AiItineraryGenerateRequest request,
      List<FastApiExistingItineraryItem> existingItinerary
  ) {
    LocalDate startDate = request.from() != null ? request.from() : trip.getStartDate();
    LocalDate endDate = request.to() != null ? request.to() : trip.getEndDate();

    if (startDate == null || endDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from/to are required when trip dates are missing");
    }
    if (startDate.isAfter(endDate)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
    }

    int travelersCount = Math.max(
        1,
        tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(trip.getId()).size()
    );
    String language = blankToDefault(request.language(), DEFAULT_LANGUAGE);
    String timezone = blankToDefault(trip.getTimezone(), "Asia/Taipei");

    return new FastApiItineraryGenerateRequest(
        trip.getTitle(),
        trip.getTitle(),
        startDate,
        endDate,
        timezone,
        travelersCount,
        blankToNull(request.travelStyle()),
        blankToNull(request.budgetLevel()),
        normalizeList(request.interests()),
        normalizeList(request.mustVisitPlaces()),
        normalizeList(request.avoidPlaces()),
        blankToNull(request.notes()),
        language,
        existingItinerary,
        true
    );
  }

  private List<FastApiExistingItineraryItem> existingItineraryContext(UUID tripId) {
    return itineraryItemRepository.findAllByTrip(tripId).stream()
        .filter(item -> blankToNull(item.getTitle()) != null)
        .limit(MAX_EXISTING_ITINERARY_ITEMS)
        .map(item -> new FastApiExistingItineraryItem(
            item.getDayDate(),
            item.getTitle().trim(),
            blankToNull(item.getLocationName()),
            truncate(blankToNull(item.getNote()), MAX_EXISTING_NOTE_LENGTH)
        ))
        .toList();
  }

  private FastApiResponse callFastApi(FastApiItineraryGenerateRequest request) {
    try {
      return restClient
          .post()
          .uri("/ai/itinerary/generate")
          .body(request)
          .retrieve()
          .body(FastApiResponse.class);
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

  private FastApiItineraryGenerateData validateResponse(FastApiResponse response) {
    if (response == null || !response.success() || response.data() == null || response.data().items() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service response is invalid");
    }
    return response.data();
  }

  private List<AiItineraryDraftDay> groupDays(List<FastApiItineraryDraftItem> items) {
    TreeMap<LocalDate, List<FastApiItineraryDraftItem>> grouped = new TreeMap<>();
    for (FastApiItineraryDraftItem item : items) {
      if (item.dayDate() == null || item.title() == null || item.title().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service response is invalid");
      }
      grouped.computeIfAbsent(item.dayDate(), ignored -> new ArrayList<>()).add(item);
    }

    List<AiItineraryDraftDay> days = new ArrayList<>();
    for (var entry : grouped.entrySet()) {
      List<AiItineraryDraftItem> dayItems = entry.getValue().stream()
          .sorted(Comparator.comparingInt(FastApiItineraryDraftItem::sortOrder))
          .map(item -> new AiItineraryDraftItem(
              item.startTime(),
              item.endTime(),
              item.title(),
              item.locationName(),
              item.mapUrl(),
              item.note(),
              item.sortOrder()
          ))
          .toList();
      days.add(new AiItineraryDraftDay(entry.getKey(), dayItems));
    }
    return days;
  }

  private static List<String> normalizeList(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String blankToDefault(String value, String defaultValue) {
    String normalized = blankToNull(value);
    return normalized == null ? defaultValue : normalized;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
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

  private record FastApiItineraryGenerateRequest(
      @JsonProperty("trip_title") String tripTitle,
      String destination,
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      @JsonProperty("start_date") LocalDate startDate,
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      @JsonProperty("end_date") LocalDate endDate,
      String timezone,
      @JsonProperty("travelers_count") int travelersCount,
      @JsonProperty("travel_style") String travelStyle,
      @JsonProperty("budget_level") String budgetLevel,
      List<String> interests,
      @JsonProperty("must_visit_places") List<String> mustVisitPlaces,
      @JsonProperty("avoid_places") List<String> avoidPlaces,
      String notes,
      String language,
      @JsonProperty("existing_itinerary") List<FastApiExistingItineraryItem> existingItinerary,
      @JsonProperty("avoid_duplicate_places") boolean avoidDuplicatePlaces
  ) {}

  private record FastApiExistingItineraryItem(
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      @JsonProperty("day_date") LocalDate dayDate,
      String title,
      @JsonProperty("location_name") String locationName,
      String note
  ) {}

  private record FastApiResponse(
      boolean success,
      FastApiItineraryGenerateData data,
      Object error
  ) {}

  private record FastApiItineraryGenerateData(
      List<FastApiItineraryDraftItem> items,
      String explanation,
      List<String> warnings,
      String source,
      boolean fallback,
      @JsonProperty("fallback_reason") String fallbackReason
  ) {}

  private record FastApiItineraryDraftItem(
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      @JsonProperty("day_date") LocalDate dayDate,
      String title,
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      @JsonProperty("start_time") LocalTime startTime,
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      @JsonProperty("end_time") LocalTime endTime,
      @JsonProperty("location_name") String locationName,
      @JsonProperty("map_url") String mapUrl,
      String note,
      @JsonProperty("sort_order") int sortOrder
  ) {}
}
