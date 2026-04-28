package com.killerplay13.tripcollab.repo;

import com.killerplay13.tripcollab.domain.NoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {
    List<NoteEntity> findByTripIdOrderByCreatedAtDesc(UUID tripId);
}
