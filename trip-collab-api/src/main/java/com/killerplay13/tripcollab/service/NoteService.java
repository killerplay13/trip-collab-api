package com.killerplay13.tripcollab.service;

import com.killerplay13.tripcollab.domain.NoteEntity;
import com.killerplay13.tripcollab.repo.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

    public List<NoteEntity> listByTrip(UUID tripId) {
        return noteRepository.findByTripIdOrderByCreatedAtDesc(tripId);
    }

    public NoteEntity get(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    }

    @Transactional
    public NoteEntity create(UUID tripId, UUID authorId, String title, String content) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        var note = NoteEntity.builder()
                .tripId(tripId)
                .authorId(authorId)
                .title(title)
                .content(content)
                .build();
        return noteRepository.save(note);
    }

    @Transactional
    public NoteEntity update(UUID id, String title, String content) {
        var note = get(id);
        if (title != null && !title.isBlank()) {
            note.setTitle(title);
        }
        if (content != null) {
            note.setContent(content);
        }
        return noteRepository.save(note);
    }

    @Transactional
    public void delete(UUID id) {
        noteRepository.deleteById(id);
    }
}
