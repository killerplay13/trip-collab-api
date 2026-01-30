package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.TripMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface TripMemberRepository extends JpaRepository<TripMemberEntity, UUID> {

    boolean existsByIdAndTripIdAndIsActiveTrue(UUID id, UUID tripId);

    List<TripMemberEntity> findByTripIdAndIsActiveTrueOrderByJoinedAtAsc(UUID tripId);

    Optional<TripMemberEntity> findByIdAndTripId(UUID id, UUID tripId);

    boolean existsByTripIdAndNickname(UUID tripId, String nickname);
}
