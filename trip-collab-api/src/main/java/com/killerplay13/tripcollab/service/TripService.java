package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.security.TripTokenUtil;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

  private final TripRepository tripRepository;

  public TripService(TripRepository tripRepository) {
    this.tripRepository = tripRepository;
  }

  @Transactional
  public CreateTripResult createTrip(String title, LocalDate startDate, LocalDate endDate, String timezone, String notes) {
    String token = TripTokenUtil.generateToken();
    String tokenHash = TripTokenUtil.sha256Hex(token);

    Trip t = new Trip();
    t.setTitle(title);
    t.setStartDate(startDate);
    t.setEndDate(endDate);
    if (timezone != null && !timezone.isBlank()) t.setTimezone(timezone);
    t.setNotes(notes);
    t.setInviteTokenHash(tokenHash);
    t.setInviteEnabled(true);

    t = tripRepository.save(t);
    return new CreateTripResult(t, token);
  }

  @Transactional(readOnly = true)
    public Trip getTrip(UUID id) {
    return tripRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
    }


  public record CreateTripResult(Trip trip, String token) {}
}
