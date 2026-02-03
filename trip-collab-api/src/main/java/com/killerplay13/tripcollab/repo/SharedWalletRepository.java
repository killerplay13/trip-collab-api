package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.SharedWalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SharedWalletRepository extends JpaRepository<SharedWalletEntity, Long> {
    Optional<SharedWalletEntity> findByTripId(UUID tripId);
}
