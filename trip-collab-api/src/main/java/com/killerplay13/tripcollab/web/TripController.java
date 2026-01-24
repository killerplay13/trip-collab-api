package com.killerplay13.tripcollab.web;

import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.service.TripService;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
public class TripController {

  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @PostMapping
  public CreateTripResponse create(@RequestBody CreateTripRequest req) {
    var result = tripService.createTrip(
        req.title(),
        req.startDate(),
        req.endDate(),
        req.timezone(),
        req.notes()
    );

    Trip t = result.trip();
    return new CreateTripResponse(
        t.getId(),
        t.getTitle(),
        t.getTimezone(),
        t.getStartDate(),
        t.getEndDate(),
        t.getNotes(),
        result.token()
    );
  }

  @GetMapping("/{tripId}")
public TripResponse get(@PathVariable UUID tripId) {
  Trip t = tripService.getTrip(tripId);
  return new TripResponse(
      t.getId(),
      t.getTitle(),
      t.getTimezone(),
      t.getStartDate(),
      t.getEndDate(),
      t.getNotes()
  );
}


  // ===== DTOs =====
  public record CreateTripRequest(
      @NotBlank String title,
      LocalDate startDate,
      LocalDate endDate,
      String timezone,
      String notes
  ) {}

  public record CreateTripResponse(
      UUID id,
      String title,
      String timezone,
      LocalDate startDate,
      LocalDate endDate,
      String notes,
      String inviteToken
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
