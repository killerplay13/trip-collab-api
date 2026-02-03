package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.SharedWalletEntity;
import com.killerplay13.tripcollab.domain.Trip;
import com.killerplay13.tripcollab.repo.SharedWalletRepository;
import com.killerplay13.tripcollab.repo.TripRepository;
import com.killerplay13.tripcollab.security.TripTokenUtil;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

  private final TripRepository tripRepository;
  private final SharedWalletRepository sharedWalletRepository;

  public TripService(TripRepository tripRepository, SharedWalletRepository sharedWalletRepository) {
    this.tripRepository = tripRepository;
    this.sharedWalletRepository = sharedWalletRepository;
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
    t.setCurrency("TWD");
    t = tripRepository.save(t);
    ensureSharedWallet(t);
    return new CreateTripResult(t, token);
  }

  @Transactional(readOnly = true)
  public Trip getTrip(UUID id) {
    return tripRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
  }

  public record CreateTripResult(Trip trip, String token) {}

  private void ensureSharedWallet(Trip trip) {
    UUID tripId = trip.getId();
    if (tripId == null) {
      throw new IllegalStateException("Trip ID is required to create shared wallet");
    }

    if (sharedWalletRepository.findByTripId(tripId).isPresent()) {
      return;
    }

    String baseCurrency = normalizeCurrency(trip.getCurrency());
    SharedWalletEntity wallet = SharedWalletEntity.builder()
        .tripId(tripId)
        .baseCurrency(baseCurrency)
        .build();
    sharedWalletRepository.save(wallet);
  }

  private static String normalizeCurrency(String ccy) {
    if (ccy == null || ccy.isBlank()) {
      throw new IllegalArgumentException("Trip currency is required");
    }
    String v = ccy.trim().toUpperCase(Locale.ROOT);
    if (v.length() != 3) {
      throw new IllegalArgumentException("Trip currency must be 3 letters");
    }
    return v;
  }
}
