package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.service.TripService;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
public class TripController {

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @PostMapping
  public ResponseEntity<CreateTripResponse> create(@RequestBody CreateTripRequest req) {
    var result = tripService.createTrip(
        req.title(),
        req.startDate(),
        req.endDate(),
        req.timezone(),
        req.notes(),
        req.creatorNickname()
    );

    Trip t = result.trip();
    return ResponseEntity.status(201).body(new CreateTripResponse(
        t.getId(),
        t.getTitle(),
        t.getTimezone(),
        t.getStartDate(),
        t.getEndDate(),
        t.getNotes(),
        result.token(),
        result.owner().memberToken(),
        result.owner().member().getId(),
        result.owner().member().getRole(),
        result.owner().member().getNickname()
    ));
  }

  @GetMapping("/{tripId}")
public ResponseEntity<TripResponse> get(@PathVariable UUID tripId) {
  Trip t = tripService.getTrip(tripId);
  return ResponseEntity.ok(new TripResponse(
      t.getId(),
      t.getTitle(),
      t.getTimezone(),
      t.getStartDate(),
      t.getEndDate(),
      t.getNotes()
  ));
}


  // ===== DTOs =====
  public record CreateTripRequest(
      @NotBlank String title,
      LocalDate startDate,
      LocalDate endDate,
      String timezone,
      String notes,
      @NotBlank String creatorNickname
  ) {}

  public record CreateTripResponse(
      UUID id,
      String title,
      String timezone,
      LocalDate startDate,
      LocalDate endDate,
      String notes,
      String inviteToken,
      String memberToken,
      UUID memberId,
      String role,
      String nickname
  ) {}

  public record TripResponse(
      UUID id,
      String title,
      String timezone,
      LocalDate startDate,
      LocalDate endDate,
      String notes
  ) {}
}
