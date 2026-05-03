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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.killerplay13.tripcollab.config.TripCollabAiProperties;
import com.killerplay13.tripcollab.domain.ItineraryItem;
import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.domain.TripMemberEntity;
import com.killerplay13.tripcollab.repo.ItineraryItemRepository;
import com.killerplay13.tripcollab.repo.TripMemberRepository;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.web.dto.ai.AiItineraryGenerateRequest;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
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
class AiItineraryServiceTest {

  @Mock
  TripRepository tripRepository;

  @Mock
  TripMemberRepository tripMemberRepository;

  @Mock
  ItineraryItemRepository itineraryItemRepository;

  @Test
  void generateMapsValidFastApiResponseIntoGroupedDays() {
    UUID tripId = UUID.randomUUID();
    Trip trip = trip(tripId);
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId))
        .thenReturn(List.of(new TripMemberEntity(), new TripMemberEntity()));
    when(itineraryItemRepository.findAllByTrip(tripId)).thenReturn(List.of(
        itineraryItem(tripId, LocalDate.of(2026, 5, 1), "Tottori Sand Dunes", "Tottori Sand Dunes", "Already planned."),
        itineraryItem(tripId, LocalDate.of(2026, 5, 2), "  ", "Blank title place", "Should be excluded")
    ));

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiItineraryService service = service(restClient(builder));

    server.expect(requestTo("http://ai.test/ai/itinerary/generate"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.trip_title").value("Hong Kong family trip"))
        .andExpect(jsonPath("$.destination").value("Hong Kong family trip"))
        .andExpect(jsonPath("$.start_date").value("2026-05-02"))
        .andExpect(jsonPath("$.end_date").value("2026-05-04"))
        .andExpect(jsonPath("$.timezone").value("Asia/Taipei"))
        .andExpect(jsonPath("$.travelers_count").value(2))
        .andExpect(jsonPath("$.travel_style").value("food"))
        .andExpect(jsonPath("$.budget_level").value("medium"))
        .andExpect(jsonPath("$.language").value("zh-TW"))
        .andExpect(jsonPath("$.avoid_duplicate_places").value(true))
        .andExpect(jsonPath("$.existing_itinerary.length()").value(1))
        .andExpect(jsonPath("$.existing_itinerary[0].day_date").value("2026-05-01"))
        .andExpect(jsonPath("$.existing_itinerary[0].title").value("Tottori Sand Dunes"))
        .andExpect(jsonPath("$.existing_itinerary[0].location_name").value("Tottori Sand Dunes"))
        .andExpect(jsonPath("$.existing_itinerary[0].note").value("Already planned."))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andRespond(withSuccess("""
            {
              "success": true,
              "data": {
                "items": [
                  {
                    "day_date": "2026-05-03",
                    "title": "Shopping",
                    "start_time": "14:00:00",
                    "end_time": "16:00:00",
                    "location_name": "Causeway Bay",
                    "map_url": null,
                    "note": "Afternoon plan",
                    "sort_order": 2
                  },
                  {
                    "day_date": "2026-05-02",
                    "title": "Arrival food walk",
                    "start_time": "10:00:00",
                    "end_time": "12:00:00",
                    "location_name": "Central",
                    "map_url": "https://maps.example/central",
                    "note": "Easy pace",
                    "sort_order": 1
                  }
                ],
                "explanation": "Food-first route.",
                "warnings": ["Check opening hours."],
                "source": "mock",
                "fallback": false,
                "fallback_reason": null
              },
              "error": null
            }
            """, MediaType.APPLICATION_JSON));

    var request = new AiItineraryGenerateRequest(
        LocalDate.of(2026, 5, 2),
        LocalDate.of(2026, 5, 4),
        List.of("food"),
        List.of("Disneyland"),
        List.of("pub"),
        "food",
        "medium",
        "Prefer transit",
        null
    );

    var response = service.generate(tripId, request);

    assertThat(response.tripId()).isEqualTo(tripId);
    assertThat(response.fallback()).isFalse();
    assertThat(response.fallbackReason()).isNull();
    assertThat(response.explanation()).isEqualTo("Food-first route.");
    assertThat(response.warnings()).containsExactly("Check opening hours.");
    assertThat(response.days()).hasSize(2);
    assertThat(response.days().get(0).dayDate()).isEqualTo(LocalDate.of(2026, 5, 2));
    assertThat(response.days().get(0).items().get(0).title()).isEqualTo("Arrival food walk");
    assertThat(response.days().get(1).dayDate()).isEqualTo(LocalDate.of(2026, 5, 3));

    server.verify();
  }

  @Test
  void generatePreservesFallbackResponse() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId)));
    when(tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId)).thenReturn(List.of());
    when(itineraryItemRepository.findAllByTrip(tripId)).thenReturn(List.of());

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiItineraryService service = service(restClient(builder));

    server.expect(requestTo("http://ai.test/ai/itinerary/generate"))
        .andRespond(withSuccess("""
            {
              "success": true,
              "data": {
                "items": [
                  {
                    "day_date": "2026-05-01",
                    "title": "Fallback draft",
                    "start_time": null,
                    "end_time": null,
                    "location_name": null,
                    "map_url": null,
                    "note": "Safe fallback draft.",
                    "sort_order": 1
                  }
                ],
                "explanation": "AI unavailable.",
                "warnings": ["AI provider failed or timed out."],
                "source": "fallback",
                "fallback": true,
                "fallback_reason": "timeout"
              },
              "error": null
            }
            """, MediaType.APPLICATION_JSON));

    var response = service.generate(tripId, defaultRequest());

    assertThat(response.fallback()).isTrue();
    assertThat(response.fallbackReason()).isEqualTo("timeout");
    assertThat(response.warnings()).containsExactly("AI provider failed or timed out.");
    assertThat(response.days()).hasSize(1);

    server.verify();
  }

  @Test
  void generateSendsEmptyExistingItineraryWhenTripHasNoItems() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId)));
    when(tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId)).thenReturn(List.of());
    when(itineraryItemRepository.findAllByTrip(tripId)).thenReturn(List.of());

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AiItineraryService service = service(restClient(builder));

    server.expect(requestTo("http://ai.test/ai/itinerary/generate"))
        .andExpect(jsonPath("$.avoid_duplicate_places").value(true))
        .andExpect(jsonPath("$.existing_itinerary.length()").value(0))
        .andRespond(withSuccess("""
            {
              "success": true,
              "data": {
                "items": [
                  {
                    "day_date": "2026-05-01",
                    "title": "Draft item",
                    "start_time": null,
                    "end_time": null,
                    "location_name": null,
                    "map_url": null,
                    "note": null,
                    "sort_order": 1
                  }
                ],
                "explanation": "Draft.",
                "warnings": [],
                "source": "mock",
                "fallback": false,
                "fallback_reason": null
              },
              "error": null
            }
            """, MediaType.APPLICATION_JSON));

    var response = service.generate(tripId, defaultRequest());

    assertThat(response.days()).hasSize(1);
    server.verify();
  }

  @Test
  void generateReturns503WhenAiIsDisabled() {
    UUID tripId = UUID.randomUUID();
    TripCollabAiProperties properties = properties(false);
    AiItineraryService service = new AiItineraryService(
        properties,
        tripRepository,
        tripMemberRepository,
        itineraryItemRepository,
        restClient(RestClient.builder())
    );

    assertThatThrownBy(() -> service.generate(tripId, defaultRequest()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(503);

    verify(tripRepository, never()).findById(tripId);
  }

  @Test
  void generateMapsTimeoutTo504() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId)));
    when(tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId)).thenReturn(List.of());
    when(itineraryItemRepository.findAllByTrip(tripId)).thenReturn(List.of());

    ClientHttpRequestFactory timeoutFactory = (uri, httpMethod) -> {
      throw new SocketTimeoutException("timed out");
    };
    AiItineraryService service = service(RestClient.builder()
        .baseUrl("http://ai.test")
        .requestFactory(timeoutFactory)
        .build());

    assertThatThrownBy(() -> service.generate(tripId, defaultRequest()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(504);
  }

  @Test
  void generateMapsConnectionErrorTo502() {
    UUID tripId = UUID.randomUUID();
    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip(tripId)));
    when(tripMemberRepository.findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(tripId)).thenReturn(List.of());
    when(itineraryItemRepository.findAllByTrip(tripId)).thenReturn(List.of());

    ClientHttpRequestFactory connectionErrorFactory = (uri, httpMethod) -> {
      throw new ConnectException("connection refused");
    };
    AiItineraryService service = service(RestClient.builder()
        .baseUrl("http://ai.test")
        .requestFactory(connectionErrorFactory)
        .build());

    assertThatThrownBy(() -> service.generate(tripId, defaultRequest()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(502);
  }

  private AiItineraryService service(RestClient restClient) {
    return new AiItineraryService(properties(true), tripRepository, tripMemberRepository, itineraryItemRepository, restClient);
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
    trip.setStartDate(LocalDate.of(2026, 5, 1));
    trip.setEndDate(LocalDate.of(2026, 5, 5));
    trip.setTimezone("Asia/Taipei");
    return trip;
  }

  private ItineraryItem itineraryItem(UUID tripId, LocalDate dayDate, String title, String locationName, String note) {
    ItineraryItem item = new ItineraryItem();
    item.setTripId(tripId);
    item.setDayDate(dayDate);
    item.setTitle(title);
    item.setLocationName(locationName);
    item.setNote(note);
    item.setSortOrder(1);
    return item;
  }

  private AiItineraryGenerateRequest defaultRequest() {
    return new AiItineraryGenerateRequest(
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        null,
        null,
        null,
        null
    );
  }
}
