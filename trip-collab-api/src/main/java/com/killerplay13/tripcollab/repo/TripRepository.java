package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.Trip;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {
  boolean existsByIdAndInviteTokenHashAndInviteEnabledTrue(UUID id, String inviteTokenHash);
}
